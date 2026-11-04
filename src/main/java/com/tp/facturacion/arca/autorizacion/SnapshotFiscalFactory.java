package com.tp.facturacion.arca.autorizacion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.HexFormat;
import java.util.Set;
import com.tp.facturacion.model.FacturaVenta;
import org.springframework.stereotype.Component;
import static com.tp.facturacion.arca.autorizacion.ContratoFiscal.*;

@Component
public class SnapshotFiscalFactory {
    private static final ZoneId ARGENTINA=ZoneId.of("America/Argentina/Buenos_Aires");
    private final Clock reloj;
    public SnapshotFiscalFactory(Clock reloj) {this.reloj=reloj.withZone(ARGENTINA);}

    public Snapshot crear(FacturaVenta factura,String emisor,Set<Integer> ivaClaseC,boolean confirmarCuitReceptor) {
        exigir(confirmarCuitReceptor,"Confirme que el documento del receptor es un CUIT.");
        exigir(factura!=null && "PENDIENTE".equals(factura.getEstado()) && (factura.getCae()==null || factura.getCae().isBlank()),"Solo se puede informar una factura pendiente sin CAE.");
        exigir(factura.getCliente()!=null,"La factura no tiene receptor.");
        String documento=factura.getCliente().getCuitCuil();
        exigir(cuitValido(documento) && cuitValido(emisor) && !documento.equals(emisor),"El CUIT del receptor debe ser válido y distinto del emisor.");
        exigir(factura.getCondicionIva()!=null && ivaClaseC!=null && ivaClaseC.contains(factura.getCondicionIva().getCodigoAfip()),"La condición IVA del receptor no está habilitada para clase C.");
        exigir(factura.getMoneda()!=null && "PES".equals(factura.getMoneda().getCodigoAfip()),"Solo se admite moneda PES con cotización 1.");
        exigir(factura.getPuntoVenta()!=null && factura.getPuntoVenta().getNumero()>0 && factura.getPuntoVenta().getNumero()<=99999,"El punto de venta no es válido.");
        exigir(factura.getFechaEmision()!=null,"La fecha fiscal es obligatoria.");
        LocalDate fecha=Instant.ofEpochMilli(factura.getFechaEmision().getTime()).atZone(ARGENTINA).toLocalDate(),hoy=LocalDate.now(reloj);
        exigir(!fecha.isBefore(hoy.minusDays(5)) && !fecha.isAfter(hoy.plusDays(5)) && YearMonth.from(fecha).equals(YearMonth.from(hoy)),"La fecha debe estar dentro de cinco días y del mes de presentación.");
        BigDecimal total=importe(factura.getImporteTotal());
        exigir(total.signum()>0,"El importe total debe ser positivo.");
        exigir(factura.getDetalles()!=null && !factura.getDetalles().isEmpty(),"La factura debe contener productos.");
        BigDecimal suma=BigDecimal.ZERO;
        for(var detalle:factura.getDetalles()) {
            exigir(detalle!=null,"El detalle fiscal no es válido.");
            exigir(Double.isFinite(detalle.getCantidad()) && detalle.getCantidad()>0,"La cantidad debe ser positiva.");
            importe(detalle.getPrecioUnitario());
            exigir(importe(detalle.getImporteNeto()).compareTo(importe(detalle.getImporteSubtotal()))==0,"El neto guardado debe coincidir con el subtotal sin IVA.");
            exigir(Double.isFinite(detalle.getPorcentajeBonificacion()) && detalle.getPorcentajeBonificacion()>=0 && detalle.getPorcentajeBonificacion()<=100,"La bonificación no es válida.");
            exigir(importe(detalle.getImporteIva()).signum()==0,"Este corte no admite IVA en el detalle.");
            suma=suma.add(importe(detalle.getImporteSubtotal()));
        }
        exigir(suma.compareTo(total)==0,"La suma de subtotales guardados no coincide con el total fiscal.");
        return new Snapshot(1,80,Long.parseLong(documento),factura.getCondicionIva().getCodigoAfip(),fecha,"PES",BigDecimal.ONE,total);
    }
    public String hash(Snapshot snapshot) {
        String canonico=snapshot.concepto()+"|"+snapshot.docTipo()+"|"+snapshot.docNumero()+"|"+snapshot.condicionIva()+"|"+snapshot.fecha()+"|"+snapshot.moneda()+"|"+decimal(snapshot.cotizacion())+"|"+decimal(snapshot.total());
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonico.getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException ex) {throw new IllegalStateException("SHA-256 no disponible.");}
    }
    static boolean cuitValido(String cuit) {
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
