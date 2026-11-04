package com.tp.facturacion.controller;

import java.io.ByteArrayInputStream;
import java.util.Arrays;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.params.provider.CsvSource;

import com.tp.facturacion.exception.GlobalExceptionHandler;
import com.tp.facturacion.reporte.FacturaExcelExporter;
import com.tp.facturacion.reporte.FacturaPdfExporter;
import com.tp.facturacion.service.FacturaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:busqueda;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false"
})
class FacturaBusquedaTests {
    @Autowired FacturaService service;
    @Autowired FacturaPdfExporter pdfExporter;
    @Autowired FacturaExcelExporter excelExporter;
    @Autowired JdbcTemplate jdbc;
    MockMvc mvc;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.standaloneSetup(new FacturaRestController(service, pdfExporter, excelExporter))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
        jdbc.update("DELETE FROM factura_venta_detalle");
        jdbc.update("DELETE FROM factura_venta");
        jdbc.update("DELETE FROM cliente");
        jdbc.update("DELETE FROM condicion_iva");
        jdbc.update("DELETE FROM punto_venta");
        jdbc.update("INSERT INTO condicion_iva (id,codigoAfip,denominacion,fechaAlta,fechaModificacion,usuario_carga_id,usuario_modificacion_id) VALUES (1,1,'Monotributo',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,1,1)");
        jdbc.update("INSERT INTO punto_venta (id,numero,descripcion,fechaAlta,fechaModificacion,usuario_carga_id,usuario_modificacion_id) VALUES (1,1,'Local',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,1,1)");
        jdbc.update("INSERT INTO cliente (id,cuitCuil,denominacion,contacto_id,domicilio_id,fechaAlta,fechaModificacion,usuario_carga_id,usuario_modificacion_id) VALUES (1,'111','Ana',1,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,1,1),(2,'222','Luis',2,2,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,1,1)");
        factura(1, 7, "2026-10-01 00:00:00", 1, "PENDIENTE", null, 100);
        factura(2, 7, "2026-10-01 00:00:00", 2, "EMITIDA", "123", 200);
        factura(3, 8, "2026-10-01 23:59:59", 1, "PENDIENTE", "   ", 300);
        factura(4, 9, "2026-10-02 00:00:00", 1, "PENDIENTE", "", 400);
        // Dos detalles en una factura: el total de resultados debe contar facturas, no ítems.
        jdbc.update("INSERT INTO factura_venta_detalle (id,factura_id,lista_precio_articulo_id,cantidad,precioUnitario,porcentajeBonificacion,importeNeto,importeIva,importeSubtotal) VALUES (1,2,1,1,100,0,100,0,100),(2,2,1,1,100,0,100,0,100)");
    }

    private void factura(long id, long numero, String fecha, long cliente, String estado, String cae, int total) {
        jdbc.update("INSERT INTO factura_venta (id,numero,fechaEmision,cliente_id,condicion_iva_id,punto_venta_id,moneda_id,importeTotal,importeCobrado,importeSaldo,estado,cae,fechaAlta,fechaModificacion,usuario_carga_id,usuario_modificacion_id) VALUES (?,?,CAST(? AS TIMESTAMP),?,1,1,1,?,0,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,1,1)", id, numero, fecha, cliente, total, total, estado, cae);
    }

    @Test
    void paginaConservaOrdenYCuentaFacturas() throws Exception {
        mvc.perform(get("/api/facturas").param("pagina", "1").param("tamanio", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contenido", hasSize(1)))
                .andExpect(jsonPath("$.contenido[0].id").value(2))
                .andExpect(jsonPath("$.contenido[0].cantidadItems").value(2))
                .andExpect(jsonPath("$.pagina").value(1)).andExpect(jsonPath("$.tamanio").value(1))
                .andExpect(jsonPath("$.totalElementos").value(4)).andExpect(jsonPath("$.totalPaginas").value(4));
    }

    @Test
    void valoresPorDefectoYPaginaFueraDelTotal() throws Exception {
        mvc.perform(get("/api/facturas")).andExpect(status().isOk())
                .andExpect(jsonPath("$.pagina").value(0)).andExpect(jsonPath("$.tamanio").value(20))
                .andExpect(jsonPath("$.contenido", hasSize(4)));
        mvc.perform(get("/api/facturas").param("pagina", "10")).andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", empty())).andExpect(jsonPath("$.totalElementos").value(4));
    }

    @Test
    void combinaFiltrosEIncluyeTodoElDiaHasta() throws Exception {
        mvc.perform(get("/api/facturas").param("fechaDesde", "2026-10-01").param("fechaHasta", "2026-10-01")
                        .param("clienteId", "1").param("numeroFactura", "8").param("estado", "PENDIENTE")
                        .param("montoMinimo", "300").param("sinInformarArca", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contenido", hasSize(1)))
                .andExpect(jsonPath("$.contenido[0].id").value(3)).andExpect(jsonPath("$.totalElementos").value(1));
    }

    @Test
    void distingueCaeNuloVacioEspaciosYAsignado() throws Exception {
        mvc.perform(get("/api/facturas").param("sinInformarArca", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contenido[*].id", contains(1, 3, 4)));
        mvc.perform(get("/api/facturas").param("sinInformarArca", "false"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contenido[*].id", contains(2)));
    }

    @ParameterizedTest
    @CsvSource({
            "numeroFactura,7,1:2", "clienteId,1,1:3:4", "estado,EMITIDA,2",
            "montoMinimo,300,3:4", "fechaDesde,2026-10-02,4", "fechaHasta,2026-10-01,1:2:3",
            "numeroFactura,999,''"
    })
    void aplicaCadaFiltroAlContenidoYAlTotal(String parametro, String valor, String ids) throws Exception {
        Integer[] esperados = ids.isEmpty() ? new Integer[0]
                : Arrays.stream(ids.split(":")).map(Integer::valueOf).toArray(Integer[]::new);
        var resultado = mvc.perform(get("/api/facturas").param(parametro, valor))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contenido", hasSize(esperados.length)))
                .andExpect(jsonPath("$.totalElementos").value(esperados.length));
        for (int indice = 0; indice < esperados.length; indice++) {
            resultado.andExpect(jsonPath("$.contenido[" + indice + "].id").value(esperados[indice]));
        }
    }

    @Test
    void reportesAplicanFiltrosCompletos() throws Exception {
        var excel = mvc.perform(get("/api/facturas/reporte/excel")
                        .param("numeroFactura", "7").param("clienteId", "2")
                        .param("sinInformarArca", "false").param("estado", "EMITIDA")
                        .param("fechaDesde", "2026-10-01").param("fechaHasta", "2026-10-01")
                        .param("montoMinimo", "200"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (var libro = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
            var hoja = libro.getSheetAt(0);
            assertEquals(2, hoja.getLastRowNum()); // encabezado, una factura, total
            assertEquals("Luis", hoja.getRow(1).getCell(2).getStringCellValue());
            assertEquals(7, hoja.getRow(1).getCell(0).getNumericCellValue());
        }
        byte[] pdf = mvc.perform(get("/api/facturas/reporte/pdf")
                        .param("numeroFactura", "7").param("clienteId", "2")
                        .param("sinInformarArca", "false").param("estado", "EMITIDA")
                        .param("fechaDesde", "2026-10-01").param("fechaHasta", "2026-10-01")
                        .param("montoMinimo", "200"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (var lector = new PdfReader(pdf)) {
            String texto = new PdfTextExtractor(lector).getTextFromPage(1);
            assertTrue(texto.contains("Luis"));
            assertFalse(texto.contains("Ana"));
            assertTrue(texto.contains("Total (1 facturas)"));
        }
    }

    @Test
    void reportesExportanTodosLosResultadosSinPaginar() throws Exception {
        for (int id = 5; id <= 29; id++) {
            factura(id, id, "2026-10-03 00:00:00", 1, "PENDIENTE", null, 100);
        }
        byte[] excel = mvc.perform(get("/api/facturas/reporte/excel").param("clienteId", "1")
                        .param("pagina", "1").param("tamanio", "1"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (var libro = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
            assertEquals(29, libro.getSheetAt(0).getLastRowNum()); // 28 facturas + total
        }
        byte[] pdf = mvc.perform(get("/api/facturas/reporte/pdf").param("clienteId", "1")
                        .param("pagina", "1").param("tamanio", "1"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (var lector = new PdfReader(pdf)) {
            var extractor = new PdfTextExtractor(lector);
            StringBuilder texto = new StringBuilder();
            for (int pagina = 1; pagina <= lector.getNumberOfPages(); pagina++) {
                texto.append(extractor.getTextFromPage(pagina));
            }
            assertTrue(texto.toString().contains("Total (28 facturas)"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"pagina=-1", "tamanio=0", "tamanio=101", "numeroFactura=0", "clienteId=-1", "pagina=abc", "sinInformarArca=quizas", "fechaDesde=incorrecta"})
    void rechazaParametrosInvalidos(String parametro) throws Exception {
        String[] partes = parametro.split("=");
        mvc.perform(get("/api/facturas").param(partes[0], partes[1]))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void rechazaRangoInvertidoEnListadoYReportes() throws Exception {
        for (String ruta : new String[]{"/api/facturas", "/api/facturas/reporte/pdf", "/api/facturas/reporte/excel"}) {
            mvc.perform(get(ruta).param("fechaDesde", "2026-10-02").param("fechaHasta", "2026-10-01"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        }
    }
    @Test void grillaSeparaNumeroLocalFiscalYPermisosDeCaeHistorico() throws Exception {
        jdbc.update("UPDATE factura_venta SET numero_fiscal=1,punto_venta_fiscal=1,tipo_comprobante_fiscal=11 WHERE id=2");
        mvc.perform(get("/api/facturas/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroFactura").value(7))
                .andExpect(jsonPath("$.numeroFiscal").value(1))
                .andExpect(jsonPath("$.puedeModificar").value(false))
                .andExpect(jsonPath("$.puedeEliminar").value(false));
        mvc.perform(get("/api/facturas/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.puedeModificar").value(true));
    }
}
