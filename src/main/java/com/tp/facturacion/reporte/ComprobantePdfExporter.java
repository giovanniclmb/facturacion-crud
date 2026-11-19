package com.tp.facturacion.reporte;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.qrcode.QRCodeWriter;
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

    private final FacturaVentaRepository facturas;
    private final IntentoArcaRepository intentos;
    private final String emisor;
    private final String domicilio;

    public ComprobantePdfExporter(FacturaVentaRepository facturas, IntentoArcaRepository intentos,
            @Value("${facturacion.emisor-denominacion:}") String emisor,
            @Value("${facturacion.emisor-domicilio:}") String domicilio) {
        this.facturas = facturas;
        this.intentos = intentos;
        this.emisor = emisor;
        this.domicilio = domicilio;
    }

    @Transactional(readOnly = true)
    public byte[] exportar(Long id) {
        var factura = facturas.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la factura con id " + id));
        boolean interno = factura.getTipoComprobante() == TipoComprobante.CONSUMIDOR_FINAL;
        IntentoArca ultimo = interno ? null : intentos.findFirstByFacturaIdOrderByCreadoDescIdDesc(id).orElse(null);
        IntentoArca fiscal = tieneAutorizacion(factura, ultimo) ? ultimo : null;

        var salida = new ByteArrayOutputStream();
        var pdf = new Document(PageSize.A4, 38, 38, 42, 42);
        PdfWriter.getInstance(pdf, salida);
        pdf.open();
        try {
            String numero = fiscal == null ? "N.º local " + (factura.getNumero() == null ? id : factura.getNumero())
                    : String.format(Locale.ROOT,
                    "%05d-%08d", fiscal.getSerie().getPuntoVenta(), fiscal.getNumero());
            pdf.add(new Paragraph((interno ? "Comprobante interno" : "Factura C") + " · " + numero,
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 17)));
            if (fiscal != null) {
                pdf.add(new Paragraph("Homologación · comprobante de prueba"));
            } else {
                pdf.add(new Paragraph((interno ? "Comprobante interno" : "Copia interna") + " · sin validez fiscal"));
                pdf.add(new Paragraph("Estado de ARCA: " + estadoSinAutorizacion(factura, ultimo, interno)));
                if (factura.getMotivoRechazo() != null && !factura.getMotivoRechazo().isBlank()) {
                    pdf.add(new Paragraph("Motivo: " + factura.getMotivoRechazo()));
                }
            }
            if ("ANULADA".equals(factura.getEstado())) pdf.add(new Paragraph("Estado del comprobante: Anulado"));
            pdf.add(new Paragraph("Fecha de emisión: " + FECHA.format(
                    Instant.ofEpochMilli(factura.getFechaEmision().getTime()))));
            if (!emisor.isBlank()) pdf.add(new Paragraph("Emisor: " + emisor));
            if (!domicilio.isBlank()) pdf.add(new Paragraph("Domicilio: " + domicilio));
            if (fiscal != null) pdf.add(new Paragraph("CUIT emisor: " + fiscal.getSerie().getEmisor()));
            var cliente = factura.getCliente();
            String receptor = cliente == null ? "Consumidor final" : cliente.getDenominacion();
            if (fiscal != null) receptor += " · CUIT " + fiscal.getDocNumero();
            pdf.add(new Paragraph("Receptor: " + receptor));
            pdf.add(new Paragraph("Condición de IVA: " + factura.getCondicionIva().getDenominacion()));

            var importes = NumberFormat.getNumberInstance(Locale.forLanguageTag("es-AR"));
            importes.setMinimumFractionDigits(2);
            importes.setMaximumFractionDigits(2);
            String moneda = factura.getMoneda().getCodigoAfip() + " ";
            var tabla = new PdfPTable(new float[]{4, 1, 1.6f, 1.2f, 1.7f});
            tabla.setWidthPercentage(100);
            tabla.setSpacingBefore(16);
            tabla.setHeaderRows(1);
            for (String titulo : new String[]{"Artículo", "Cant.", "Precio unit.", "Bonif. %", "Subtotal"}) {
                var celda = celda(titulo, false);
                celda.setBackgroundColor(new Color(236, 240, 245));
                tabla.addCell(celda);
            }
            for (var item : factura.getDetalles().stream().sorted(Comparator.comparing(FacturaVentaDetalle::getId)).toList()) {
                tabla.addCell(celda(item.getDescripcion(), false));
                tabla.addCell(celda(importes.format(item.getCantidad()), true));
                tabla.addCell(celda(importes.format(item.getPrecioUnitario()), true));
                tabla.addCell(celda(importes.format(item.getPorcentajeBonificacion()), true));
                tabla.addCell(celda(importes.format(item.getImporteSubtotal()), true));
            }
            pdf.add(tabla);
            var total = new Paragraph("Total: " + moneda + importes.format(factura.getImporteTotal()),
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13));
            total.setAlignment(Element.ALIGN_RIGHT);
            total.setSpacingBefore(8);
            pdf.add(total);
            if (fiscal != null) pdf.add(autorizacion(fiscal));
        } finally {
            pdf.close();
        }
        return salida.toByteArray();
    }

    private boolean tieneAutorizacion(FacturaVenta factura, IntentoArca intento) {
        return intento != null && intento.getEstado() == EstadoIntentoArca.AUTORIZADO
                && intento.getNumero() != null && intento.getVencimiento() != null
                && intento.getCae() != null && intento.getCae().equals(factura.getCae());
    }

    private String estadoSinAutorizacion(FacturaVenta factura, IntentoArca intento, boolean interno) {
        if (interno) return "No aplica";
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
        imagen.setBorder(org.openpdf.text.Rectangle.NO_BORDER);
        tabla.addCell(imagen);
        var texto = celda("CAE: " + fiscal.getCae() + "\nVencimiento: " + FECHA.format(fiscal.getVencimiento()), false);
        texto.setBorder(org.openpdf.text.Rectangle.NO_BORDER);
        texto.setVerticalAlignment(Element.ALIGN_MIDDLE);
        tabla.addCell(texto);
        return tabla;
    }

    static String urlQr(IntentoArca intento) {
        var serie = intento.getSerie();
        var datos = intento.getSnapshot();
        String json = String.format(Locale.ROOT, """
                {"ver":1,"fecha":"%s","cuit":%d,"ptoVta":%d,"tipoCmp":%d,"nroCmp":%d,"importe":%s,"moneda":"%s","ctz":%s,"tipoDocRec":%d,"nroDocRec":%d,"tipoCodAut":"E","codAut":%d}
                """.strip(), datos.fecha(), Long.parseLong(serie.getEmisor()), serie.getPuntoVenta(),
                serie.getTipo(), intento.getNumero(), datos.total().toPlainString(), datos.moneda(),
                datos.cotizacion().stripTrailingZeros().toPlainString(), datos.docTipo(), datos.docNumero(),
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
        var celda = new PdfPCell(new Phrase(texto == null ? "" : texto));
        celda.setPadding(6);
        celda.setHorizontalAlignment(numero ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
        return celda;
    }
}
