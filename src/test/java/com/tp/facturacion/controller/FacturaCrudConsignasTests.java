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
        "spring.config.import=", "arca.habilitado=false",
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
        for (String tabla : new String[]{"factura_venta_detalle", "factura_venta", "lista_precio_articulo", "articulo", "lista_precio", "cliente", "contacto", "domicilio", "usuario", "condicion_iva", "punto_venta", "tipo_moneda"}) {
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
