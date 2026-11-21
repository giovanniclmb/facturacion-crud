package com.tp.facturacion.arca.autorizacion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.HexFormat;
import java.util.Set;
import com.tp.facturacion.arca.ArcaProperties;
import com.tp.facturacion.model.FacturaVenta;
import org.springframework.stereotype.Component;
import static com.tp.facturacion.arca.autorizacion.ContratoFiscal.*;

@Component
public class SnapshotFiscalFactory {
    public static final BigDecimal LIMITE_IDENTIFICACION_FACTURA_B = new BigDecimal("10000000.00");
    static final int DOCUMENTO_NO_IDENTIFICADO = 99;
    static final int CONDICION_CONSUMIDOR_FINAL = 5;
    static final int ALICUOTA_WSFE_21 = 5;
    private static final BigDecimal ALICUOTA_21 = new BigDecimal("21");
    private static final BigDecimal FACTOR_PRECIO_FINAL = new BigDecimal("1.21");
    private static final ZoneId ARGENTINA=ZoneId.of("America/Argentina/Buenos_Aires");
    private final Clock reloj;
    public SnapshotFiscalFactory(Clock reloj) {this.reloj=reloj.withZone(ARGENTINA);}

    public Snapshot crear(FacturaVenta factura,String emisor,Set<Integer> ivaClase,boolean confirmarReceptor) {
        exigir(confirmarReceptor,"Confirme los datos del receptor.");
        exigir(factura!=null && "PENDIENTE".equals(factura.getEstado()) && (factura.getCae()==null || factura.getCae().isBlank()),"Solo se puede informar una factura pendiente sin CAE.");
        int tipo = ArcaProperties.tipoFiscal(factura.getTipoComprobante());
        exigir(tipo != 0, "El comprobante interno no permite autorización fiscal.");
        boolean esB = tipo == ArcaProperties.FACTURA_B;
        exigir(cuitValido(emisor), "El CUIT del emisor debe ser válido.");
        exigir(factura.getCondicionIva()!=null && ivaClase!=null && ivaClase.contains(factura.getCondicionIva().getCodigoAfip()),"La condición IVA del receptor no está habilitada para esta clase.");
        int condicionIva = factura.getCondicionIva().getCodigoAfip();
        exigir(factura.getMoneda()!=null && "PES".equals(factura.getMoneda().getCodigoAfip()),"Solo se admite moneda PES con cotización 1.");
        exigir(factura.getPuntoVenta()!=null && factura.getPuntoVenta().getNumero()>0 && factura.getPuntoVenta().getNumero()<=99999,"El punto de venta no es válido.");
        exigir(factura.getFechaEmision()!=null,"La fecha fiscal es obligatoria.");
        LocalDate fecha=Instant.ofEpochMilli(factura.getFechaEmision().getTime()).atZone(ARGENTINA).toLocalDate(),hoy=LocalDate.now(reloj);
        exigir(!fecha.isBefore(hoy.minusDays(5)) && !fecha.isAfter(hoy.plusDays(5)) && YearMonth.from(fecha).equals(YearMonth.from(hoy)),"La fecha debe estar dentro de cinco días y del mes de presentación.");
        BigDecimal total=importe(factura.getImporteTotal());
        exigir(total.signum()>0,"El importe total debe ser positivo.");
        int docTipo;
        long docNumero;
        if (esB) {
            exigir(factura.getCliente() == null && condicionIva == CONDICION_CONSUMIDOR_FINAL,
                    "La Factura B de este flujo corresponde a consumidor final.");
            validarDocumentoFacturaB(factura.getDocumentoReceptorTipo(), factura.getDocumentoReceptorNumero(), total);
            docTipo = factura.getDocumentoReceptorTipo() == null ? DOCUMENTO_NO_IDENTIFICADO : factura.getDocumentoReceptorTipo();
            docNumero = factura.getDocumentoReceptorNumero() == null ? 0 : factura.getDocumentoReceptorNumero();
            exigir(!Long.toString(docNumero).equals(emisor), "El documento del receptor debe ser distinto del emisor.");
        } else {
            exigir(factura.getCliente()!=null,"La factura no tiene receptor.");
            String documento=factura.getCliente().getCuitCuil();
            exigir(cuitValido(documento) && !documento.equals(emisor),"El CUIT del receptor debe ser válido y distinto del emisor.");
            docTipo = 80;
            docNumero = Long.parseLong(documento);
        }
        exigir(factura.getDetalles()!=null && !factura.getDetalles().isEmpty(),"La factura debe contener productos.");
        BigDecimal suma=BigDecimal.ZERO, neto=BigDecimal.ZERO, iva=BigDecimal.ZERO;
        for(var detalle:factura.getDetalles()) {
            exigir(detalle!=null,"El detalle fiscal no es válido.");
            exigir(Double.isFinite(detalle.getCantidad()) && detalle.getCantidad()>0,"La cantidad debe ser positiva.");
            importe(detalle.getPrecioUnitario());
            exigir(Double.isFinite(detalle.getPorcentajeBonificacion()) && detalle.getPorcentajeBonificacion()>=0 && detalle.getPorcentajeBonificacion()<=100,"La bonificación no es válida.");
            BigDecimal subtotal = importe(detalle.getImporteSubtotal());
            BigDecimal netoDetalle = importe(detalle.getImporteNeto());
            BigDecimal ivaDetalle = importe(detalle.getImporteIva());
            if (esB) {
                exigir(detalle.getIvaPorcentaje() != null && detalle.getIvaPorcentaje().compareTo(ALICUOTA_21) == 0,
                        "La Factura B requiere IVA del 21 % explícito en el detalle.");
            } else {
                exigir(ivaDetalle.signum()==0,"La Factura C no admite IVA en el detalle.");
            }
            exigir(netoDetalle.add(ivaDetalle).compareTo(subtotal)==0,"El neto y el IVA guardados deben sumar el subtotal.");
            suma=suma.add(subtotal);
            neto=neto.add(netoDetalle);
            iva=iva.add(ivaDetalle);
        }
        exigir(suma.compareTo(total)==0,"La suma de subtotales guardados no coincide con el total fiscal.");
        if (esB) {
            exigir(neto.compareTo(netoFacturaB(total)) == 0, "El neto guardado no corresponde al IVA del 21 %.");
            return new Snapshot(1,docTipo,docNumero,condicionIva,fecha,"PES",BigDecimal.ONE,total,neto,iva);
        }
        return new Snapshot(1,docTipo,docNumero,condicionIva,fecha,"PES",BigDecimal.ONE,total);
    }

    public static void validarDocumentoFacturaB(Integer tipo, Long numero, BigDecimal total) {
        if (tipo == null && numero == null) {
            exigir(total.compareTo(LIMITE_IDENTIFICACION_FACTURA_B) < 0,
                    "La Factura B requiere identificar al consumidor final desde $ 10.000.000.");
            return;
        }
        exigir(tipo != null && numero != null && numero > 0, "Complete el tipo y número de documento del consumidor final.");
        boolean valido = switch (tipo) {
            case 96 -> numero <= 99_999_999;
            case 86, 87 -> cuitValido(Long.toString(numero));
            default -> false;
        };
        exigir(valido, "El documento del consumidor final debe ser un DNI, CUIL o CDI válido.");
    }

    static BigDecimal netoFacturaB(BigDecimal total) {
        return total.divide(FACTOR_PRECIO_FINAL, 2, RoundingMode.HALF_UP);
    }
    public String hash(Snapshot snapshot) {
        String canonico=snapshot.concepto()+"|"+snapshot.docTipo()+"|"+snapshot.docNumero()+"|"+snapshot.condicionIva()+"|"+snapshot.fecha()+"|"+snapshot.moneda()+"|"+decimal(snapshot.cotizacion())+"|"+decimal(snapshot.total());
        if (snapshot.docTipo() != 80) {
            canonico += "|" + decimal(snapshot.neto()) + "|" + decimal(snapshot.iva());
        }
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonico.getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException ex) {throw new IllegalStateException("SHA-256 no disponible.");}
    }
    public static boolean cuitValido(String cuit) {
        if(cuit==null || !cuit.matches("[0-9]{11}") || cuit.equals("00000000000")) return false;
        int[] pesos={5,4,3,2,7,6,5,4,3,2};int suma=0;
        for(int i=0;i<10;i++) suma+=(cuit.charAt(i)-'0')*pesos[i];
        int digito=11-suma%11;if(digito==11) digito=0; if(digito==10) return false;
        return digito==cuit.charAt(10)-'0';
    }
    private static BigDecimal importe(double valor) {
        exigir(Double.isFinite(valor) && valor>=0,"Los importes deben ser finitos y no negativos.");
        BigDecimal importe=BigDecimal.valueOf(valor).setScale(2,RoundingMode.HALF_UP);
        exigir(importe.precision()<=18,"El importe excede la precisión fiscal admitida.");return importe;
    }
    private static String decimal(BigDecimal n) {return n.stripTrailingZeros().toPlainString();}
    private static void exigir(boolean condicion,String mensaje) {if(!condicion) throw new IllegalArgumentException(mensaje);}
}
