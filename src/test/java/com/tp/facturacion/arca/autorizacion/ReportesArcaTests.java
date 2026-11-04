package com.tp.facturacion.arca.autorizacion;

import java.io.ByteArrayInputStream;
import java.util.Date;
import java.util.List;
import com.tp.facturacion.dto.FacturaReporteDto;
import com.tp.facturacion.reporte.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReportesArcaTests {
    FacturaReporteDto factura() {
        return new FacturaReporteDto(1L, 37L, new Date(), "Cliente ficticio", "Consumidor Final",
                "Sucursal", 125.25, 1L, "PENDIENTE", null);
    }
    @Test void excelDistingueNumeroLocalFiscalYBorrador() throws Exception {
        var autorizada = factura();
        autorizada.setNumeroFiscal(1L); autorizada.setPuntoVentaFiscal(1);
        autorizada.setTipoComprobanteFiscal(11); autorizada.setEstadoFiscal(EstadoIntentoArca.AUTORIZADO);
        var bytes = new FacturaExcelExporter().exportar(List.of(autorizada, factura()));
        try(var libro = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var hoja = libro.getSheetAt(0);
            assertEquals("N° local", hoja.getRow(0).getCell(0).getStringCellValue());
            assertEquals("00001-00000001 (tipo 11)", hoja.getRow(1).getCell(7).getStringCellValue());
            assertEquals("AUTORIZADO", hoja.getRow(1).getCell(8).getStringCellValue());
            assertEquals("Sin CAE", hoja.getRow(2).getCell(8).getStringCellValue());
        }
    }
    @Test void pdfDistingueFiscalYBorrador() throws Exception {
        var autorizada = factura();
        autorizada.setNumeroFiscal(1L); autorizada.setPuntoVentaFiscal(1);
        autorizada.setTipoComprobanteFiscal(11); autorizada.setEstadoFiscal(EstadoIntentoArca.AUTORIZADO);
        try(var lector = new PdfReader(new FacturaPdfExporter().exportar(List.of(autorizada, factura())))) {
            var texto = new PdfTextExtractor(lector).getTextFromPage(1);
            assertTrue(texto.contains("N° local"));
            assertTrue(texto.contains("00001-00000001"));
            assertTrue(texto.contains("Sin CAE"));
        }
    }
}
