package com.tp.facturacion.controller;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:crud-consignas;DB_CLOSE_DELAY=-1",
        "spring.config.import=", "arca.habilitado=false", "arca.puntos-venta-exclusivos=99001",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false",
        "facturacion.usuario-sistema-id=999"
})
class FacturaCrudConsignasTests {
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    MockMvc mvc;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
        for (String tabla : new String[]{"arca_intento", "arca_serie", "factura_venta_detalle", "factura_venta", "lista_precio_articulo", "articulo", "lista_precio", "cliente", "contacto", "domicilio", "usuario", "condicion_iva", "punto_venta", "tipo_moneda"}) {
            jdbc.update("DELETE FROM " + tabla);
        }
        jdbc.execute("ALTER TABLE factura_venta_detalle ALTER COLUMN id RESTART WITH 100");
        jdbc.update("INSERT INTO usuario (id,usuario,clave,nombre,apellido) VALUES (1,'original','no-usada','Original','Prueba'),(999,'sistema','no-usada','Sistema','Prueba')");
        for (int id = 1; id <= 2; id++) {
            jdbc.update("INSERT INTO contacto (id) VALUES (?)", id);
            jdbc.update("INSERT INTO domicilio (id) VALUES (?)", id);
            auditada("cliente", "id,cuitCuil,denominacion,contacto_id,domicilio_id", id + ",'20111','Cliente " + id + "'," + id + "," + id);
        }
        auditada("condicion_iva", "id,codigoAfip,denominacion", "1,1,'Monotributo'");
        auditada("punto_venta", "id,numero,descripcion", "1,1,'Local'");
        auditada("tipo_moneda", "id,codigoAfip,denominacion,simbolo", "1,'PES','Pesos','$'");
        auditada("articulo", "id,codigo,denominacion", "1,'ART-1','Lapiz'");
        auditada("lista_precio", "id,codigo,denominacion", "1,'MIN','Minorista'");
        auditada("lista_precio_articulo", "id,articulo_id,lista_precio_id,precioVenta", "1,1,1,100");
        auditada("articulo", "id,codigo,denominacion", "2,'ART-2','Cuaderno'");
        auditada("lista_precio_articulo", "id,articulo_id,lista_precio_id,precioVenta", "2,2,1,25.50");
        jdbc.execute("ALTER TABLE factura_venta ALTER COLUMN id RESTART WITH 100");
        jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");
    }

    private void auditada(String tabla, String columnas, String valores) {
        jdbc.update("INSERT INTO " + tabla + " (" + columnas + ",fechaAlta,fechaModificacion,usuario_carga_id,usuario_modificacion_id) VALUES (" + valores + ",TIMESTAMP '2026-10-01 12:00:00',TIMESTAMP '2026-10-01 12:00:00',1,1)");
    }

    private String alta(String detalles) {
        return "{\"puntoVentaId\":1,\"clienteId\":1,\"condicionIvaId\":1,\"tipoMonedaId\":1,\"detalles\":" + detalles + "}";
    }

    private long crear() throws Exception {
        String respuesta = mvc.perform(post("/api/facturas").contentType(MediaType.APPLICATION_JSON)
                .content(alta("[{\"articuloId\":1,\"cantidad\":2,\"porcentajeBonificacion\":10},{\"articuloId\":2,\"cantidad\":3}]")))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/facturas/100"))
                .andExpect(jsonPath("$.importeTotal").value(256.50))
                .andExpect(jsonPath("$.cantidadItems").value(2)).andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andReturn().getResponse().getContentAsString();
        assertTrue(respuesta.contains("\"id\":100"));
        return 100L;
    }

    @Test
    void internoSinClienteUsaGeneralComoAlternativaYPuedeImprimirse() throws Exception {
        auditada("condicion_iva", "id,codigoAfip,denominacion", "2,5,'Consumidor final'");
        jdbc.update("UPDATE lista_precio SET codigo='GEN', denominacion='General' WHERE id=1");
        auditada("lista_precio", "id,codigo,denominacion", "2,'MAY','Mayorista'");
        auditada("lista_precio_articulo", "id,articulo_id,lista_precio_id,precioVenta", "3,1,2,75");
        mvc.perform(post("/api/facturas").contentType(MediaType.APPLICATION_JSON).content("""
                {"tipoComprobante":"CONSUMIDOR_FINAL","puntoVentaId":1,"tipoMonedaId":1,"listaPrecioId":2,"operacionGuardado":"b8eb577c-c13b-4e47-95dd-f21bdddedab2",
                 "detalles":[{"articuloId":1,"cantidad":2},{"articuloId":2,"cantidad":2}]}
                """))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.importeTotal").value(201.0))
                .andExpect(jsonPath("$.tipoComprobante").value("CONSUMIDOR_FINAL"));
        mvc.perform(get("/api/facturas/100/arca"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mensaje").value("No aplica. Es un comprobante interno."));
        assertNull(jdbc.queryForObject("SELECT cliente_id FROM factura_venta WHERE id=100", Long.class));
        mvc.perform(get("/api/facturas/100/detalle"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.listaPrecioId").value(2))
                .andExpect(jsonPath("$.detalles[1].listaPrecioArticuloId").value(2));
        byte[] pdf = mvc.perform(get("/api/facturas/100/comprobante")).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("inline")))
                .andReturn().getResponse().getContentAsByteArray();
        try (var lector = new org.openpdf.text.pdf.PdfReader(pdf)) {
            String texto = new org.openpdf.text.pdf.parser.PdfTextExtractor(lector).getTextFromPage(1);
            assertTrue(texto.contains("Factura B - Consumidor Final"));
            assertTrue(texto.contains("Emisor"));
            assertTrue(texto.contains("Receptor"));
            assertTrue(texto.contains("Subtotal: $ 201,00"));
            assertTrue(texto.contains("Total: $ 201,00"));
            assertEquals(org.openpdf.text.PageSize.A4.getWidth(), lector.getPageSize(1).getWidth(), 1f);
            assertFalse(texto.contains("PES"));
            assertTrue(texto.contains("sin validez fiscal"));
        }
    }

    @Test
    void guardarFacturaCSinConfirmacionConservaElAltaSinCae() throws Exception {
        jdbc.update("UPDATE lista_precio SET codigo='GEN', denominacion='General' WHERE id=1");
        mvc.perform(post("/api/facturas").contentType(MediaType.APPLICATION_JSON).content("""
                {"tipoComprobante":"FACTURA_C","puntoVentaId":1,"clienteId":1,"condicionIvaId":1,
                 "tipoMonedaId":1,"listaPrecioId":1,
                 "operacionGuardado":"1be85e76-18f2-4215-8c0a-e34284ae547a",
                 "detalles":[{"articuloId":1,"cantidad":1}]}
                """))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.mensajeArca").isNotEmpty());
        byte[] pdf = mvc.perform(get("/api/facturas/100/comprobante"))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn().getResponse().getContentAsByteArray();
        try (var lector = new org.openpdf.text.pdf.PdfReader(pdf)) {
            String texto = new org.openpdf.text.pdf.parser.PdfTextExtractor(lector).getTextFromPage(1);
            assertTrue(texto.contains("sin validez fiscal"));
            assertTrue(texto.contains("Sin CAE"));
        }
    }

    @Test
    void convierteBEnCConPuntoFiscalYVuelveABAntesDelPrimerIntento() throws Exception {
        prepararConversion();
        mvc.perform(post("/api/facturas").contentType(MediaType.APPLICATION_JSON).content("""
                {"tipoComprobante":"CONSUMIDOR_FINAL","puntoVentaId":1,"tipoMonedaId":1,"listaPrecioId":1,
                 "operacionGuardado":"1be85e76-18f2-4215-8c0a-e34284ae547a",
                 "detalles":[{"articuloId":1,"cantidad":1}]}
                """)).andExpect(status().isCreated());

        jdbc.update("UPDATE punto_venta SET fechaBaja = CURRENT_TIMESTAMP WHERE id = 2");
        mvc.perform(put("/api/facturas/100").contentType(MediaType.APPLICATION_JSON)
                .content(edicion("FACTURA_C", 1, 0, ",\"clienteId\":2,\"condicionIvaId\":1")))
                .andExpect(status().isConflict());
        jdbc.update("UPDATE punto_venta SET fechaBaja = NULL WHERE id = 2");

        mvc.perform(put("/api/facturas/100").contentType(MediaType.APPLICATION_JSON)
                .content(edicion("FACTURA_C", 2, 0, ",\"clienteId\":2,\"condicionIvaId\":1")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.tipoComprobante").value("FACTURA_C"))
                .andExpect(jsonPath("$.clienteDenominacion").value("Cliente 2"))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"));
        assertEquals(2L, jdbc.queryForObject("SELECT punto_venta_id FROM factura_venta WHERE id=100", Long.class));

        long revision = jdbc.queryForObject("SELECT revision FROM factura_venta WHERE id=100", Long.class);
        mvc.perform(put("/api/facturas/100").contentType(MediaType.APPLICATION_JSON)
                .content(edicion("CONSUMIDOR_FINAL", 2, revision, "")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tipoComprobante").value("CONSUMIDOR_FINAL"));
        assertNull(jdbc.queryForObject("SELECT cliente_id FROM factura_venta WHERE id=100", Long.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM arca_intento", Integer.class));
    }

    @Test
    void despuesDeUnaSolicitudRechazadaSePuedeCambiarClientePeroNoTipo() throws Exception {
        prepararConversion();
        mvc.perform(post("/api/facturas").contentType(MediaType.APPLICATION_JSON).content("""
                {"tipoComprobante":"FACTURA_C","puntoVentaId":2,"tipoMonedaId":1,"listaPrecioId":1,
                 "clienteId":1,"condicionIvaId":1,"operacionGuardado":"1be85e76-18f2-4215-8c0a-e34284ae547a",
                 "detalles":[{"articuloId":1,"cantidad":1}]}
                """)).andExpect(status().isCreated());
        jdbc.update("INSERT INTO arca_serie (id,ambiente,emisor,punto_venta,tipo) VALUES (21,'HOMOLOGACION','30000000007',99001,11)");
        jdbc.update("""
                INSERT INTO arca_intento (id,factura_id,serie_id,usuario_iniciador_id,propietario,estado,numero,
                    concepto,doc_tipo,doc_numero,condicion_iva,fecha,moneda,cotizacion,total,hash,creado,actualizado,errores,observaciones,version)
                VALUES (RANDOM_UUID(),100,21,999,RANDOM_UUID(),'RECHAZADO',1,
                    1,80,30000000007,1,CURRENT_DATE,'PES',1,100,'prueba',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'','',0)
                """);

        mvc.perform(get("/api/facturas/100/detalle")).andExpect(status().isOk())
                .andExpect(jsonPath("$.puedeModificar").value(true))
                .andExpect(jsonPath("$.puedeCambiarTipo").value(false));
        mvc.perform(put("/api/facturas/100").contentType(MediaType.APPLICATION_JSON)
                .content(edicion("CONSUMIDOR_FINAL", 2, 0, "")))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/facturas/100").contentType(MediaType.APPLICATION_JSON)
                .content(edicion("FACTURA_C", 2, 0, ",\"clienteId\":2,\"condicionIvaId\":1")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.clienteDenominacion").value("Cliente 2"))
                .andExpect(jsonPath("$.estadoFiscal").value("RECHAZADO"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM arca_intento", Integer.class));
    }

    @Test
    void unComprobanteHistoricoSinTipoConservaSuCliente() throws Exception {
        prepararConversion();
        crear();
        mvc.perform(get("/api/facturas/100/detalle")).andExpect(status().isOk())
                .andExpect(jsonPath("$.puedeCambiarTipo").value(false));
        mvc.perform(put("/api/facturas/100").contentType(MediaType.APPLICATION_JSON)
                .content(edicion("CONSUMIDOR_FINAL", 1, 0, "")))
                .andExpect(status().isConflict());
        assertEquals(1L, jdbc.queryForObject("SELECT cliente_id FROM factura_venta WHERE id=100", Long.class));
    }

    private void prepararConversion() {
        jdbc.update("UPDATE lista_precio SET codigo='GEN', denominacion='General' WHERE id=1");
        auditada("condicion_iva", "id,codigoAfip,denominacion", "2,5,'Consumidor final'");
        auditada("punto_venta", "id,numero,descripcion", "2,99001,'Homologación'");
    }

    private String edicion(String tipo, long punto, long revision, String receptor) {
        return """
                {"tipoComprobante":"%s","puntoVentaId":%d,"revision":%d,"listaPrecioId":1,
                 "detalles":[{"articuloId":1,"cantidad":1}]%s}
                """.formatted(tipo, punto, revision, receptor);
    }

    @Test
    void altaPersisteCabeceraYMultiplesDetallesConCascadaEImportesDeFacturaC() throws Exception {
        long id = crear();
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM factura_venta", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM factura_venta_detalle WHERE factura_id=?", Integer.class, id));
        assertEquals(256.50, jdbc.queryForObject("SELECT importeSaldo FROM factura_venta WHERE id=?", Double.class, id));
        assertEquals(0D, jdbc.queryForObject("SELECT SUM(importeIva) FROM factura_venta_detalle", Double.class));
        assertEquals(256.50, jdbc.queryForObject("SELECT SUM(importeNeto) FROM factura_venta_detalle", Double.class));
        assertEquals(256.50, jdbc.queryForObject("SELECT SUM(importeSubtotal) FROM factura_venta_detalle", Double.class));
        assertEquals(100D, jdbc.queryForObject("SELECT precioUnitario FROM factura_venta_detalle WHERE lista_precio_articulo_id=1", Double.class));
        mvc.perform(get("/api/facturas/100")).andExpect(status().isOk()).andExpect(jsonPath("$.importeTotal").value(256.50));
    }

    @Test
    void referenciaInvalidaRevierteTodaElAlta() throws Exception {
        mvc.perform(post("/api/facturas").contentType(MediaType.APPLICATION_JSON)
                .content(alta("[{\"articuloId\":1,\"cantidad\":2},{\"articuloId\":9999,\"cantidad\":1}]")))
                .andExpect(status().isNotFound());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM factura_venta", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM factura_venta_detalle", Integer.class));
    }

    @Test
    void putActualizaCabeceraReemplazaItemsYEliminaHuerfanos() throws Exception {
        crear();
        List<Long> anteriores = jdbc.queryForList("SELECT id FROM factura_venta_detalle", Long.class);
        mvc.perform(put("/api/facturas/100").contentType(MediaType.APPLICATION_JSON)
                .content("{\"clienteId\":2,\"observaciones\":\"Corregida\",\"fechaEmision\":\"2026-10-03\",\"detalles\":[{\"articuloId\":2,\"listaPrecioArticuloId\":2,\"cantidad\":2,\"porcentajeBonificacion\":20}]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.clienteDenominacion").value("Cliente 2"))
                .andExpect(jsonPath("$.fechaEmision").value("2026-10-03"))
                .andExpect(jsonPath("$.importeTotal").value(40.80)).andExpect(jsonPath("$.cantidadItems").value(1));
        assertEquals("Corregida", jdbc.queryForObject("SELECT observaciones FROM factura_venta WHERE id=100", String.class));
        assertEquals(40.80, jdbc.queryForObject("SELECT importeSaldo FROM factura_venta WHERE id=100", Double.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM factura_venta_detalle", Integer.class));
        for (long anterior : anteriores) assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM factura_venta_detalle WHERE id=?", Integer.class, anterior));
        mvc.perform(delete("/api/facturas/100")).andExpect(status().isNoContent()).andExpect(content().string(""));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM factura_venta", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM factura_venta_detalle", Integer.class));
    }

    @Test
    void inexistentesDevuelven404YDetallesVacios400() throws Exception {
        mvc.perform(put("/api/facturas/9999").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/facturas/9999")).andExpect(status().isNotFound());
        mvc.perform(post("/api/facturas").contentType(MediaType.APPLICATION_JSON).content(alta("[]")))
                .andExpect(status().isBadRequest());
        crear();
        mvc.perform(put("/api/facturas/100").contentType(MediaType.APPLICATION_JSON).content("{\"detalles\":[]}"))
                .andExpect(status().isBadRequest());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM factura_venta_detalle", Integer.class));
    }

    @Test
    void putConReferenciaInvalidaRevierteCabeceraYReemplazoDeDetalles() throws Exception {
        crear();
        var cabecera = jdbc.queryForMap("SELECT * FROM factura_venta WHERE id=100");
        var detalles = jdbc.queryForList("SELECT * FROM factura_venta_detalle ORDER BY id");
        mvc.perform(put("/api/facturas/100").contentType(MediaType.APPLICATION_JSON)
                .content("{\"clienteId\":2,\"observaciones\":\"No guardar\",\"detalles\":[{\"articuloId\":2,\"cantidad\":1},{\"articuloId\":9999,\"cantidad\":1}]}"))
                .andExpect(status().isNotFound());
        assertEquals(cabecera, jdbc.queryForMap("SELECT * FROM factura_venta WHERE id=100"));
        assertEquals(detalles, jdbc.queryForList("SELECT * FROM factura_venta_detalle ORDER BY id"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"lista_precio_articulo", "articulo", "lista_precio"})
    void precioExplicitoRechazaCualquieraDeLasTresEntidadesInactivas(String tabla) throws Exception {
        jdbc.update("UPDATE " + tabla + " SET fechaBaja=CURRENT_TIMESTAMP WHERE id=1");
        mvc.perform(post("/api/facturas").contentType(MediaType.APPLICATION_JSON)
                .content(alta("[{\"articuloId\":1,\"listaPrecioArticuloId\":1,\"cantidad\":1}]")))
                .andExpect(status().isBadRequest());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM factura_venta", Integer.class));
    }

    @Test
    void precioAutomaticoOmiteListaDadaDeBajaYUsaElUltimoPrecioCompletamenteActivo() throws Exception {
        auditada("lista_precio", "id,codigo,denominacion", "2,'BAJA','Lista inactiva'");
        jdbc.update("UPDATE lista_precio SET fechaBaja=CURRENT_TIMESTAMP WHERE id=2");
        auditada("lista_precio_articulo", "id,articulo_id,lista_precio_id,precioVenta", "3,1,2,999");
        mvc.perform(post("/api/facturas").contentType(MediaType.APPLICATION_JSON)
                .content(alta("[{\"articuloId\":1,\"cantidad\":1}]")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.importeTotal").value(100));
    }

    @Test
    void precioAutomaticoRechazaArticuloDadoDeBajaSinPersistirFactura() throws Exception {
        jdbc.update("UPDATE articulo SET fechaBaja=CURRENT_TIMESTAMP WHERE id=1");
        mvc.perform(post("/api/facturas").contentType(MediaType.APPLICATION_JSON)
                .content(alta("[{\"articuloId\":1,\"cantidad\":1}]")))
                .andExpect(status().isNotFound());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM factura_venta", Integer.class));
    }
}
