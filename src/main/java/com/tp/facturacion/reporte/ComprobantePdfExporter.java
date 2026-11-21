package com.tp.facturacion.reporte;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.qrcode.QRCodeWriter;
import com.tp.facturacion.arca.ArcaProperties;
import com.tp.facturacion.arca.autorizacion.EstadoIntentoArca;
import com.tp.facturacion.arca.autorizacion.IntentoArca;
import com.tp.facturacion.arca.autorizacion.IntentoArcaRepository;
import com.tp.facturacion.exception.RecursoNoEncontradoException;
import com.tp.facturacion.model.FacturaVenta;
import com.tp.facturacion.model.FacturaVentaDetalle;
import com.tp.facturacion.model.TipoComprobante;
import com.tp.facturacion.repository.FacturaVentaRepository;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Comparator;
import java.util.Locale;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.FontFactory;
import org.openpdf.text.Rectangle;
import org.openpdf.text.Image;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ComprobantePdfExporter {
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/uuuu")
            .withZone(ZoneId.of("America/Argentina/Mendoza"));

    private static final Color BORDE = new Color(169, 178, 190);
    private static final Color FONDO_CLARO = new Color(243, 245, 248);

    private final FacturaVentaRepository facturas;
    private final IntentoArcaRepository intentos;
    private final ArcaProperties arca;
    private final String emisor;
    private final String domicilio;

    public ComprobantePdfExporter(FacturaVentaRepository facturas, IntentoArcaRepository intentos, ArcaProperties arca,
            @Value("${facturacion.emisor-denominacion:}") String emisor,
            @Value("${facturacion.emisor-domicilio:}") String domicilio) {
        this.facturas = facturas;
        this.intentos = intentos;
        this.arca = arca;
        this.emisor = emisor;
        this.domicilio = domicilio;
    }

    @Transactional(readOnly = true)
    public byte[] exportar(Long id) {
        var factura = facturas.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la factura con id " + id));
        TipoComprobante tipo = factura.getTipoComprobante();
        boolean interno = tipo != null && tipo.esInterno();
        boolean facturaB = tipo == TipoComprobante.FACTURA_B;
        IntentoArca ultimo = interno ? null : intentos.findFirstByFacturaIdOrderByCreadoDescIdDesc(id).orElse(null);
        IntentoArca fiscal = tieneAutorizacion(factura, ultimo, facturaB ? 6 : 11) ? ultimo : null;
        var snapshot = fiscal == null ? null : fiscal.getSnapshot();

        var salida = new ByteArrayOutputStream();
        var pdf = new Document(PageSize.A4, 38, 38, 42, 42);
        PdfWriter.getInstance(pdf, salida);
        pdf.open();
        try {
            pdf.add(cabecera(factura, fiscal, interno));
            if (!interno) pdf.add(receptor(factura, fiscal, ultimo, facturaB));

            var importes = NumberFormat.getNumberInstance(Locale.forLanguageTag("es-AR"));
            importes.setMinimumFractionDigits(2);
            importes.setMaximumFractionDigits(2);
            String codigoMoneda = snapshot == null ? factura.getMoneda().getCodigoAfip() : snapshot.moneda();
            String moneda = "PES".equals(codigoMoneda) ? "$ " : codigoMoneda + " ";
            var tabla = new PdfPTable(new float[]{4, 1, 1.6f, 1.2f, 1.7f});
            tabla.setWidthPercentage(100);
            tabla.setSpacingBefore(15);
            tabla.setHeaderRows(1);
            String[] columnas = {"Artículo", "Cant.", "Precio unit.", "Bonif. %", "Subtotal"};
            for (int i = 0; i < columnas.length; i++) {
                var titulo = celda(columnas[i], i != 0, true, 9);
                titulo.setBackgroundColor(FONDO_CLARO);
                tabla.addCell(titulo);
            }
            BigDecimal subtotal = BigDecimal.ZERO;
            for (var item : factura.getDetalles().stream().sorted(Comparator.comparing(FacturaVentaDetalle::getId)).toList()) {
                tabla.addCell(celda(item.getDescripcion(), false));
                tabla.addCell(celda(importes.format(item.getCantidad()), true));
                tabla.addCell(celda(moneda + importes.format(item.getPrecioUnitario()), true));
                tabla.addCell(celda(importes.format(item.getPorcentajeBonificacion()), true));
                tabla.addCell(celda(moneda + importes.format(item.getImporteSubtotal()), true));
                subtotal = subtotal.add(BigDecimal.valueOf(item.getImporteSubtotal()));
            }
            pdf.add(tabla);
            var total = snapshot == null ? BigDecimal.valueOf(factura.getImporteTotal()) : snapshot.total();
            pdf.add(totales(moneda + importes.format(subtotal),
                    moneda + importes.format(total)));
            if (facturaB) {
                var iva = snapshot == null ? factura.getDetalles().stream()
                        .map(item -> BigDecimal.valueOf(item.getImporteIva()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add) : snapshot.iva();
                pdf.add(transparenciaFiscal(moneda + importes.format(iva), moneda + importes.format(BigDecimal.ZERO)));
            }
            if (fiscal != null) pdf.add(autorizacion(fiscal));
        } finally {
            pdf.close();
        }
        return salida.toByteArray();
    }

    private PdfPTable cabecera(FacturaVenta factura, IntentoArca fiscal, boolean interno) {
        var tabla = new PdfPTable(new float[]{4, 1, 4});
        tabla.setWidthPercentage(100);
        tabla.setKeepTogether(true);

        var leyenda = celda(fiscal != null ? "Homologación · comprobante de prueba"
                : (interno ? "Comprobante interno" : "Copia interna") + " · sin validez fiscal", false, true, 10);
        leyenda.setColspan(3);
        leyenda.setHorizontalAlignment(Element.ALIGN_CENTER);
        leyenda.setBackgroundColor(FONDO_CLARO);
        tabla.addCell(leyenda);

        var datosEmisor = bloque("Emisor");
        if (fiscal == null || fiscal.getSerie().getEmisor().equals(arca.getCuit())) {
            agregarLinea(datosEmisor, emisor, false);
            agregarLinea(datosEmisor, domicilio.isBlank() ? "" : "Domicilio: " + domicilio, false);
        }
        if (fiscal != null) agregarLinea(datosEmisor, "CUIT emisor: " + fiscal.getSerie().getEmisor(), false);
        if (fiscal != null && factura.getTipoComprobante() == TipoComprobante.FACTURA_B)
            agregarLinea(datosEmisor, "IVA Responsable Inscripto", false);
        tabla.addCell(datosEmisor);

        String clase = interno ? "X" : factura.getTipoComprobante() == TipoComprobante.FACTURA_B ? "B" : "C";
        var letra = celda(clase, false, true, 25);
        letra.setHorizontalAlignment(Element.ALIGN_CENTER);
        letra.setVerticalAlignment(Element.ALIGN_MIDDLE);
        tabla.addCell(letra);

        String numero = fiscal == null
                ? "N.º local " + (factura.getNumero() == null ? factura.getId() : factura.getNumero())
                : String.format(Locale.ROOT, "%05d-%08d", fiscal.getSerie().getPuntoVenta(), fiscal.getNumero());
        var identificacion = bloque("Factura " + clase, true);
        agregarLinea(identificacion, numero, true);
        String fecha = fiscal == null ? FECHA.format(Instant.ofEpochMilli(factura.getFechaEmision().getTime()))
                : FECHA.format(fiscal.getSnapshot().fecha());
        agregarLinea(identificacion, "Fecha de emisión: " + fecha, true);
        if ("ANULADA".equals(factura.getEstado()))
            agregarLinea(identificacion, "Estado del comprobante: Anulado", true);
        tabla.addCell(identificacion);
        return tabla;
    }

    private PdfPTable receptor(FacturaVenta factura, IntentoArca fiscal, IntentoArca ultimo, boolean facturaB) {
        var tabla = new PdfPTable(1);
        tabla.setWidthPercentage(100);
        tabla.setKeepTogether(true);

        var bloque = bloque("Receptor");
        if (facturaB) {
            agregarLinea(bloque, "A CONSUMIDOR FINAL", false);
            Integer tipoDoc = fiscal == null ? factura.getDocumentoReceptorTipo() : Integer.valueOf(fiscal.getSnapshot().docTipo());
            Long numeroDoc = fiscal == null ? factura.getDocumentoReceptorNumero() : Long.valueOf(fiscal.getSnapshot().docNumero());
            if (tipoDoc != null && numeroDoc != null && tipoDoc != 99) {
                String documento = switch (tipoDoc) {
                    case 96 -> "DNI";
                    case 86 -> "CUIL";
                    case 87 -> "CDI";
                    case 80 -> "CUIT";
                    default -> "Documento";
                };
                agregarLinea(bloque, documento + " " + numeroDoc, false);
            }
        } else {
            var cliente = factura.getCliente();
            String denominacion = cliente == null ? "Consumidor final" : cliente.getDenominacion();
            if (fiscal != null) denominacion += " · CUIT " + fiscal.getSnapshot().docNumero();
            agregarLinea(bloque, denominacion, false);
        }
        agregarLinea(bloque, "Condición de IVA: " + factura.getCondicionIva().getDenominacion(), false);
        if (fiscal == null) {
            agregarLinea(bloque, "Estado de ARCA: " + estadoSinAutorizacion(factura, ultimo), false);
            if (factura.getMotivoRechazo() != null && !factura.getMotivoRechazo().isBlank()) {
                agregarLinea(bloque, "Motivo: " + factura.getMotivoRechazo(), false);
            }
        }
        tabla.addCell(bloque);
        return tabla;
    }

    private PdfPTable totales(String subtotal, String total) {
        var tabla = new PdfPTable(1);
        tabla.setWidthPercentage(42);
        tabla.setHorizontalAlignment(Element.ALIGN_RIGHT);
        tabla.setSpacingBefore(13);
        tabla.setKeepTogether(true);
        tabla.addCell(celda("Subtotal: " + subtotal, true));
        var destacado = celda("Total: " + total, true, true, 14);
        destacado.setBackgroundColor(FONDO_CLARO);
        tabla.addCell(destacado);
        return tabla;
    }

    private PdfPTable transparenciaFiscal(String iva, String otrosImpuestos) {
        var tabla = new PdfPTable(1);
        tabla.setWidthPercentage(62);
        tabla.setHorizontalAlignment(Element.ALIGN_LEFT);
        tabla.setSpacingBefore(13);
        tabla.setKeepTogether(true);
        var contenido = bloque("Régimen de Transparencia Fiscal al Consumidor (Ley 27.743)");
        agregarLinea(contenido, "IVA contenido: " + iva, false);
        agregarLinea(contenido, "Otros impuestos nacionales indirectos: " + otrosImpuestos, false);
        tabla.addCell(contenido);
        return tabla;
    }

    private PdfPCell bloque(String titulo) {
        return bloque(titulo, false);
    }

    private PdfPCell bloque(String titulo, boolean derecha) {
        var bloque = new PdfPCell();
        bloque.setPadding(12);
        bloque.setBorderColor(BORDE);
        var encabezado = new Paragraph(titulo, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12));
        encabezado.setAlignment(derecha ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
        bloque.addElement(encabezado);
        return bloque;
    }

    private void agregarLinea(PdfPCell bloque, String texto, boolean derecha) {
        if (texto.isBlank()) return;
        var parrafo = new Paragraph(texto, FontFactory.getFont(FontFactory.HELVETICA, 10));
        parrafo.setSpacingBefore(3);
        parrafo.setAlignment(derecha ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
        bloque.addElement(parrafo);
    }

    private boolean tieneAutorizacion(FacturaVenta factura, IntentoArca intento, int clase) {
        return intento != null && intento.getEstado() == EstadoIntentoArca.AUTORIZADO
                && intento.getSerie().getTipo() == clase
                && intento.getNumero() != null && intento.getVencimiento() != null
                && intento.getCae() != null && intento.getCae().equals(factura.getCae());
    }

    private String estadoSinAutorizacion(FacturaVenta factura, IntentoArca intento) {
        if (factura.getCae() != null && !factura.getCae().isBlank()) return "CAE histórico sin verificar";
        if (intento == null) return "Sin CAE";
        return switch (intento.getEstado()) {
            case RECHAZADO -> "Rechazada · sin CAE";
            case PREPARANDO, ENVIANDO -> "Solicitud en curso · sin CAE confirmado";
            case INCIERTO -> "Resultado incierto · sin CAE confirmado";
            case FALLIDO_ANTES_ENVIO -> "Fallo previo al envío · sin CAE";
            case AUTORIZADO -> "Autorización sin verificar";
        };
    }

    private PdfPTable autorizacion(IntentoArca fiscal) {
        var tabla = new PdfPTable(new float[]{1, 4});
        tabla.setWidthPercentage(100);
        tabla.setKeepTogether(true);
        tabla.setSpacingBefore(15);
        var imagen = new PdfPCell(imagenQr(urlQr(fiscal)));
        imagen.setBorder(Rectangle.TOP);
        imagen.setBorderColor(BORDE);
        imagen.setPaddingTop(10);
        tabla.addCell(imagen);
        var texto = celda("CAE: " + fiscal.getCae() + "\nVencimiento: " + FECHA.format(fiscal.getVencimiento()), false);
        texto.setBorder(Rectangle.TOP);
        texto.setVerticalAlignment(Element.ALIGN_MIDDLE);
        tabla.addCell(texto);
        return tabla;
    }

    static String urlQr(IntentoArca intento) {
        var serie = intento.getSerie();
        var datos = intento.getSnapshot();
        String documento = datos.docTipo() == 99 ? "" : String.format(Locale.ROOT,
                "\"tipoDocRec\":%d,\"nroDocRec\":%d,", datos.docTipo(), datos.docNumero());
        String json = String.format(Locale.ROOT, """
                {"ver":1,"fecha":"%s","cuit":%d,"ptoVta":%d,"tipoCmp":%d,"nroCmp":%d,"importe":%s,"moneda":"%s","ctz":%s,%s"tipoCodAut":"E","codAut":%d}
                """.strip(), datos.fecha(), Long.parseLong(serie.getEmisor()), serie.getPuntoVenta(),
                serie.getTipo(), intento.getNumero(), datos.total().toPlainString(), datos.moneda(),
                datos.cotizacion().stripTrailingZeros().toPlainString(), documento,
                Long.parseLong(intento.getCae()));
        String base64 = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        return "https://www.arca.gob.ar/fe/qr/?p=" + URLEncoder.encode(base64, StandardCharsets.UTF_8);
    }

    private Image imagenQr(String contenido) {
        try {
            var qr = new QRCodeWriter().encode(contenido, BarcodeFormat.QR_CODE, 240, 240);
            byte[] pixeles = new byte[qr.getWidth() * qr.getHeight()];
            for (int y = 0; y < qr.getHeight(); y++) {
                for (int x = 0; x < qr.getWidth(); x++) {
                    pixeles[y * qr.getWidth() + x] = qr.get(x, y) ? 0 : (byte) 255;
                }
            }
            var imagen = Image.getInstance(qr.getWidth(), qr.getHeight(), 1, 8, pixeles);
            imagen.scaleAbsolute(90, 90);
            return imagen;
        } catch (com.google.zxing.WriterException | org.openpdf.text.BadElementException error) {
            throw new IllegalStateException("No se pudo generar el QR del comprobante", error);
        }
    }

    private PdfPCell celda(String texto, boolean numero) {
        return celda(texto, numero, false, 10);
    }

    private PdfPCell celda(String texto, boolean numero, boolean negrita, int tamanio) {
        var fuente = FontFactory.getFont(negrita ? FontFactory.HELVETICA_BOLD : FontFactory.HELVETICA, tamanio);
        var celda = new PdfPCell(new Phrase(texto == null ? "" : texto, fuente));
        celda.setBorderColor(BORDE);
        celda.setPadding(7);
        celda.setHorizontalAlignment(numero ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
        return celda;
    }
}
