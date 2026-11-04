package com.tp.facturacion.controller;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
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
        "spring.datasource.url=jdbc:h2:mem:cae-inmutable;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false",
        "facturacion.usuario-sistema-id=999"
})
class FacturaCaeInmutableTests {
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
        auditada("factura_venta", "id,numero,fechaEmision,cliente_id,condicion_iva_id,punto_venta_id,moneda_id,importeTotal,importeCobrado,importeSaldo,estado,cae,observaciones,resultadoAfip,caeFechaVencimiento",
                "1,7,TIMESTAMP '2026-10-01 12:00:00',1,1,1,1,200,50,150,'EMITIDA',' 12345 ','Original','A',TIMESTAMP '2026-10-10 00:00:00'");
        jdbc.update("INSERT INTO factura_venta_detalle (id,factura_id,lista_precio_articulo_id,descripcion,cantidad,precioUnitario,porcentajeBonificacion,importeNeto,importeIva,importeSubtotal) VALUES (1,1,1,'Original',2,100,0,200,0,200)");
    }

    private void auditada(String tabla, String columnas, String valores) {
        jdbc.update("INSERT INTO " + tabla + " (" + columnas + ",fechaAlta,fechaModificacion,usuario_carga_id,usuario_modificacion_id) VALUES (" + valores + ",TIMESTAMP '2026-10-01 12:00:00',TIMESTAMP '2026-10-01 12:00:00',1,1)");
    }

    static Stream<String> cambios() {
        return Stream.of("{}", "{\"observaciones\":\"Nueva\"}", "{\"estado\":\"PENDIENTE\"}",
                "{\"estado\":\"EMITIDA\"}", "{\"estado\":\"ANULADA\"}", "{\"clienteId\":2}",
                "{\"fechaEmision\":\"2026-10-02\"}",
                "{\"detalles\":[{\"articuloId\":1,\"listaPrecioArticuloId\":1,\"cantidad\":3}]}",
                "{\"observaciones\":\"Nueva\",\"clienteId\":2,\"estado\":\"ANULADA\",\"detalles\":[{\"articuloId\":1,\"cantidad\":3}]}");
    }

    @ParameterizedTest
    @MethodSource("cambios")
    void conCaeRechazaCualquierPutSinCambiarDatos(String body) throws Exception {
        Map<String, Object> factura = factura();
        List<Map<String, Object>> detalles = detalles();
        mvc.perform(put("/api/facturas/1").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));
        assertEquals(factura, factura(), "Toda cabecera y auditoría deben permanecer idénticas");
        assertEquals(detalles, detalles(), "Todos los datos e identidades de detalles deben permanecer idénticos");
    }

    @Test
    void conCaeRechazaDeleteSinCambiarDatos() throws Exception {
        Map<String, Object> factura = factura();
        List<Map<String, Object>> detalles = detalles();
        mvc.perform(delete("/api/facturas/1")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
        assertEquals(factura, factura());
        assertEquals(detalles, detalles());
    }

    @Test
    void caeBloqueaAunqueYaEsteAnulada() throws Exception {
        jdbc.update("UPDATE factura_venta SET estado='ANULADA',fechaAnulacion=TIMESTAMP '2026-10-02 12:00:00' WHERE id=1");
        conCaeRechazaCualquierPutSinCambiarDatos("{}");
        conCaeRechazaDeleteSinCambiarDatos();
    }

    @Test
    void caeSeVerificaAntesDeResolverUsuarioSistema() throws Exception {
        jdbc.update("DELETE FROM usuario WHERE id=999");
        conCaeRechazaCualquierPutSinCambiarDatos("{}");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void sinCaePermiteEditarAnularYEliminar(String cae) throws Exception {
        jdbc.update("UPDATE factura_venta SET cae=?,estado='PENDIENTE' WHERE id=1", cae);
        mvc.perform(put("/api/facturas/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"observaciones\":\"Nueva\",\"clienteId\":2,\"detalles\":[{\"articuloId\":1,\"listaPrecioArticuloId\":1,\"cantidad\":3}]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.importeTotal").value(300));
        assertEquals("Nueva", jdbc.queryForObject("SELECT observaciones FROM factura_venta WHERE id=1", String.class));
        assertEquals(2L, jdbc.queryForObject("SELECT cliente_id FROM factura_venta WHERE id=1", Long.class));
        assertEquals(999L, jdbc.queryForObject("SELECT usuario_modificacion_id FROM factura_venta WHERE id=1", Long.class));
        assertEquals(3D, jdbc.queryForObject("SELECT cantidad FROM factura_venta_detalle WHERE factura_id=1", Double.class));
        mvc.perform(put("/api/facturas/1").contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"ANULADA\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ANULADA"));
        assertNotNull(jdbc.queryForObject("SELECT fechaAnulacion FROM factura_venta WHERE id=1", java.sql.Timestamp.class));
        Map<String, Object> anulada = factura();
        List<Map<String, Object>> detalles = detalles();
        mvc.perform(put("/api/facturas/1").contentType(MediaType.APPLICATION_JSON).content("{\"observaciones\":\"Otra\"}"))
                .andExpect(status().isConflict());
        assertEquals(anulada, factura());
        assertEquals(detalles, detalles());
        mvc.perform(delete("/api/facturas/1")).andExpect(status().isNoContent());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM factura_venta", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM factura_venta_detalle", Integer.class));
    }

    private Map<String, Object> factura() {
        return jdbc.queryForMap("SELECT * FROM factura_venta WHERE id=1");
    }

    private List<Map<String, Object>> detalles() {
        return jdbc.queryForList("SELECT * FROM factura_venta_detalle WHERE factura_id=1 ORDER BY id");
    }
}
