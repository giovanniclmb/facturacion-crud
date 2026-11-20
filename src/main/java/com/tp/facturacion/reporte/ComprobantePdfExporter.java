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
            pdf.add(cabecera(factura, fiscal, interno));
            pdf.add(receptor(factura, fiscal, ultimo, interno));

            var importes = NumberFormat.getNumberInstance(Locale.forLanguageTag("es-AR"));
            importes.setMinimumFractionDigits(2);
            importes.setMaximumFractionDigits(2);
            String codigoMoneda = factura.getMoneda().getCodigoAfip();
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
            pdf.add(totales(moneda + importes.format(subtotal),
                    moneda + importes.format(factura.getImporteTotal())));
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
        agregarLinea(datosEmisor, emisor, false);
        agregarLinea(datosEmisor, domicilio.isBlank() ? "" : "Domicilio: " + domicilio, false);
        if (fiscal != null) agregarLinea(datosEmisor, "CUIT emisor: " + fiscal.getSerie().getEmisor(), false);
        tabla.addCell(datosEmisor);

        var letra = celda(interno ? "B" : "C", false, true, 25);
        letra.setHorizontalAlignment(Element.ALIGN_CENTER);
        letra.setVerticalAlignment(Element.ALIGN_MIDDLE);
        tabla.addCell(letra);

        String numero = fiscal == null
                ? "N.º local " + (factura.getNumero() == null ? factura.getId() : factura.getNumero())
                : String.format(Locale.ROOT, "%05d-%08d", fiscal.getSerie().getPuntoVenta(), fiscal.getNumero());
        var identificacion = bloque(interno ? "Factura B - Consumidor Final" : "Factura C", true);
        agregarLinea(identificacion, numero, true);
        agregarLinea(identificacion, "Fecha de emisión: " + FECHA.format(
                Instant.ofEpochMilli(factura.getFechaEmision().getTime())), true);
        tabla.addCell(identificacion);
        return tabla;
    }

    private PdfPTable receptor(FacturaVenta factura, IntentoArca fiscal, IntentoArca ultimo, boolean interno) {
        var tabla = new PdfPTable(1);
        tabla.setWidthPercentage(100);
        tabla.setKeepTogether(true);

        var bloque = bloque("Receptor");
        var cliente = factura.getCliente();
        String denominacion = cliente == null ? "Consumidor final" : cliente.getDenominacion();
        if (fiscal != null) denominacion += " · CUIT " + fiscal.getDocNumero();
        agregarLinea(bloque, denominacion, false);
        agregarLinea(bloque, "Condición de IVA: " + factura.getCondicionIva().getDenominacion(), false);
        if (fiscal == null) {
            agregarLinea(bloque, "Estado de ARCA: " + estadoSinAutorizacion(factura, ultimo, interno), false);
            if (factura.getMotivoRechazo() != null && !factura.getMotivoRechazo().isBlank()) {
                agregarLinea(bloque, "Motivo: " + factura.getMotivoRechazo(), false);
            }
        }
        if ("ANULADA".equals(factura.getEstado())) agregarLinea(bloque, "Estado del comprobante: Anulado", false);
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
