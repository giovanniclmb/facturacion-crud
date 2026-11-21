package com.tp.facturacion.reporte;

import com.tp.facturacion.arca.ArcaProperties;
import com.tp.facturacion.arca.autorizacion.ContratoFiscal.Snapshot;
import com.tp.facturacion.arca.autorizacion.EstadoIntentoArca;
import com.tp.facturacion.arca.autorizacion.IntentoArca;
import com.tp.facturacion.arca.autorizacion.IntentoArcaRepository;
import com.tp.facturacion.arca.autorizacion.SerieArca;
import com.tp.facturacion.dto.FacturaReporteDto;
import com.tp.facturacion.model.CondicionIva;
import com.tp.facturacion.model.FacturaVenta;
import com.tp.facturacion.model.FacturaVentaDetalle;
import com.tp.facturacion.model.TipoComprobante;
import com.tp.facturacion.model.TipoMoneda;
import com.tp.facturacion.repository.FacturaVentaRepository;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ComprobantePdfExporterTests {
    private static final LocalDate FECHA = LocalDate.of(2026, 11, 21);

    @Test
    void unaXUsaNumeroLocalYSuPdfNoConsultaArca() throws Exception {
        var factura = factura(TipoComprobante.FACTURA_X);
        factura.setEstado("ANULADA");
        var intentos = mock(IntentoArcaRepository.class);
        String pdf = texto(exportar(factura, intentos, "30000000007"));

        assertTrue(pdf.contains("Factura X"));
        assertTrue(pdf.contains("N.º local 18"));
        assertTrue(pdf.contains("sin validez fiscal"));
        assertTrue(pdf.contains("Anulado"));
        assertFalse(pdf.contains("IVA contenido"));
        assertFalse(pdf.contains("CAE:"));
        verifyNoInteractions(intentos);
    }

    @Test
    void unaBPendienteMuestraIvaYDocumentoSinCAE() throws Exception {
        var factura = factura(TipoComprobante.FACTURA_B);
        factura.setDocumentoReceptorTipo(96);
        factura.setDocumentoReceptorNumero(35000000L);
        var intentos = mock(IntentoArcaRepository.class);
        when(intentos.findFirstByFacturaIdOrderByCreadoDescIdDesc(18L)).thenReturn(Optional.empty());
        String pdf = texto(exportar(factura, intentos, "30000000007"));

        assertTrue(pdf.contains("Factura B"));
        assertTrue(pdf.contains("A CONSUMIDOR FINAL"));
        assertTrue(pdf.contains("DNI 35000000"));
        assertTrue(pdf.contains("sin validez fiscal"));
        assertTrue(pdf.contains("IVA contenido: $ 21.000,00"));
        assertFalse(pdf.contains("CAE:"));
    }

    @Test
    void unaBAutorizadaUsaElIvaYReceptorConfirmadosDelSnapshot() throws Exception {
        var factura = factura(TipoComprobante.FACTURA_B);
        factura.setCae("12345678901234");
        factura.getDetalles().getFirst().setImporteIva(1900);
        var intento = intento(6, 99, 0);
        when(intento.getEstado()).thenReturn(EstadoIntentoArca.AUTORIZADO);
        when(intento.getVencimiento()).thenReturn(FECHA.plusDays(10));
        var intentos = mock(IntentoArcaRepository.class);
        when(intentos.findFirstByFacturaIdOrderByCreadoDescIdDesc(18L)).thenReturn(Optional.of(intento));
        String pdf = texto(exportar(factura, intentos, "30000000007"));

        assertTrue(pdf.contains("Factura B"));
        assertTrue(pdf.contains("99002-00000001"));
        assertTrue(pdf.contains("IVA Responsable Inscripto"));
        assertTrue(pdf.contains("A CONSUMIDOR FINAL"));
        assertTrue(pdf.contains("IVA contenido: $ 21.000,00"));
        assertTrue(pdf.contains("CAE: 12345678901234"));
        assertTrue(pdf.contains("Vencimiento: 01/12/2026"));
        assertFalse(pdf.contains("CUIT 0"));
        assertFalse(pdf.contains("sin validez fiscal"));

        String pdfDeOtroEmisor = texto(exportar(factura, intentos, "20123456786"));
        assertTrue(pdfDeOtroEmisor.contains("CUIT emisor: 30000000007"));
        assertFalse(pdfDeOtroEmisor.contains("Emisor de ejemplo"));
    }

    @Test
    void losListadosDistinguenLasFacturasByX() throws Exception {
        var reportes = List.of(reporte(TipoComprobante.FACTURA_B), reporte(TipoComprobante.FACTURA_X));
        String pdf = texto(new FacturaPdfExporter().exportar(reportes));
        assertTrue(pdf.contains("Factura B"));
        assertTrue(pdf.contains("Factura X"));
        var excel = new FacturaExcelExporter().exportar(reportes);
        try (var libro = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
            var hoja = libro.getSheetAt(0);
            assertEquals("Tipo", hoja.getRow(0).getCell(9).getStringCellValue());
            assertEquals("Factura B", hoja.getRow(1).getCell(9).getStringCellValue());
            assertEquals("Factura X", hoja.getRow(2).getCell(9).getStringCellValue());
            assertEquals("No aplica", hoja.getRow(2).getCell(8).getStringCellValue());
        }
    }

    @Test
    void omiteDocumentoDelQrDeUnaFacturaBAnonima() {
        var json = jsonQr(intento(6, 99, 0));

        assertTrue(json.contains("\"tipoCmp\":6"));
        assertFalse(json.contains("tipoDocRec"));
        assertFalse(json.contains("nroDocRec"));
    }

    @Test
    void incluyeDocumentoSiElReceptorFueIdentificado() {
        var json = jsonQr(intento(6, 96, 35000000));

        assertTrue(json.contains("\"tipoDocRec\":96"));
        assertTrue(json.contains("\"nroDocRec\":35000000"));
    }

    private static IntentoArca intento(int clase, int tipoDoc, long numeroDoc) {
        var intento = mock(IntentoArca.class);
        var serie = mock(SerieArca.class);
        when(serie.getTipo()).thenReturn(clase);
        when(serie.getPuntoVenta()).thenReturn(99002);
        when(serie.getEmisor()).thenReturn("30000000007");
        when(intento.getSerie()).thenReturn(serie);
        when(intento.getNumero()).thenReturn(1L);
        when(intento.getCae()).thenReturn("12345678901234");
        when(intento.getSnapshot()).thenReturn(new Snapshot(1, tipoDoc, numeroDoc, 5,
                FECHA, "PES", BigDecimal.ONE, BigDecimal.valueOf(121000),
                BigDecimal.valueOf(100000), BigDecimal.valueOf(21000)));
        return intento;
    }

    private static String jsonQr(IntentoArca intento) {
        String parametro = URI.create(ComprobantePdfExporter.urlQr(intento)).getQuery().substring(2);
        return new String(Base64.getDecoder().decode(parametro), StandardCharsets.UTF_8);
    }

    private static FacturaVenta factura(TipoComprobante tipo) {
        var factura = new FacturaVenta();
        factura.setId(18L);
        factura.setNumero(18L);
        factura.setTipoComprobante(tipo);
        factura.setFechaEmision(Date.from(Instant.parse("2026-11-21T15:00:00Z")));
        factura.setImporteTotal(121000);
        var moneda = new TipoMoneda();
        moneda.setCodigoAfip("PES");
        factura.setMoneda(moneda);
        var condicion = new CondicionIva();
        condicion.setDenominacion("Consumidor Final");
        factura.setCondicionIva(condicion);
        var item = new FacturaVentaDetalle();
        item.setId(1L);
        item.setDescripcion("Equipo de ejemplo");
        item.setCantidad(1);
        item.setPrecioUnitario(121000);
        item.setImporteIva(21000);
        item.setImporteSubtotal(121000);
        factura.addDetalle(item);
        return factura;
    }

    private static FacturaReporteDto reporte(TipoComprobante tipo) {
        var factura = new FacturaReporteDto(1L, 18L, new Date(), "Consumidor final", "Consumidor Final",
                "Sucursal", 121000, 1L, "PENDIENTE", null);
        factura.setTipoComprobante(tipo);
        return factura;
    }

    private static byte[] exportar(FacturaVenta factura, IntentoArcaRepository intentos, String emisorCuit) {
        var facturas = mock(FacturaVentaRepository.class);
        when(facturas.findById(18L)).thenReturn(Optional.of(factura));
        var arca = new ArcaProperties();
        arca.setCuit(emisorCuit);
        return new ComprobantePdfExporter(facturas, intentos, arca, "Emisor de ejemplo", "").exportar(18L);
    }

    private static String texto(byte[] bytes) throws Exception {
        try (var lector = new PdfReader(bytes)) {
            return new PdfTextExtractor(lector).getTextFromPage(1);
        }
    }
}
