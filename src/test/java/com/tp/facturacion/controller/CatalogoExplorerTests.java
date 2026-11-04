package com.tp.facturacion.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:explorers;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false"
})
class CatalogoExplorerTests {
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
        jdbc.update("INSERT INTO usuario (id,usuario,clave,nombre,apellido) VALUES (1,'sistema','no-usada','Sistema','Prueba')");
        for (int id = 1; id <= 4; id++) {
            jdbc.update("INSERT INTO contacto (id) VALUES (?)", id);
            jdbc.update("INSERT INTO domicilio (id) VALUES (?)", id);
        }
        auditada("condicion_iva", "id,codigoAfip,denominacion", "1,1,'Monotributo'");
        auditada("punto_venta", "id,numero,descripcion", "1,1,'Local'");
        auditada("tipo_moneda", "id,codigoAfip,denominacion,simbolo", "1,'PES','Pesos','$'");
        auditada("cliente", "id,cuitCuil,denominacion,contacto_id,domicilio_id", "1,'20111','Ana',1,1");
        auditada("cliente", "id,cuitCuil,denominacion,contacto_id,domicilio_id", "2,'20222','Ana',2,2");
        auditada("cliente", "id,cuitCuil,denominacion,contacto_id,domicilio_id", "3,'20333','Cliente %_ literal',3,3");
        auditada("cliente", "id,cuitCuil,denominacion,contacto_id,domicilio_id", "4,'20444','Baja',4,4");
        jdbc.update("UPDATE cliente SET fechaBaja=CURRENT_TIMESTAMP WHERE id=4");
        auditada("articulo", "id,codigo,denominacion", "1,'ART-1','Lapiz'");
        auditada("articulo", "id,codigo,denominacion", "2,'COD%_','Articulo literal'");
        auditada("articulo", "id,codigo,denominacion", "3,'BAJA','Articulo inactivo'");
        jdbc.update("UPDATE articulo SET fechaBaja=CURRENT_TIMESTAMP WHERE id=3");
        auditada("lista_precio", "id,codigo,denominacion", "1,'MAY','Mayorista'");
        auditada("lista_precio", "id,codigo,denominacion", "2,'MIN','Minorista'");
        auditada("lista_precio", "id,codigo,denominacion", "3,'OFF','Lista inactiva'");
        jdbc.update("UPDATE lista_precio SET fechaBaja=CURRENT_TIMESTAMP WHERE id=3");
        auditada("lista_precio_articulo", "id,articulo_id,lista_precio_id,precioVenta", "1,1,1,100");
        auditada("lista_precio_articulo", "id,articulo_id,lista_precio_id,precioVenta", "2,1,2,200");
        auditada("lista_precio_articulo", "id,articulo_id,lista_precio_id,precioVenta", "3,2,1,300");
        auditada("lista_precio_articulo", "id,articulo_id,lista_precio_id,precioVenta", "4,3,1,400");
        auditada("lista_precio_articulo", "id,articulo_id,lista_precio_id,precioVenta", "5,1,3,500");
        auditada("lista_precio_articulo", "id,articulo_id,lista_precio_id,precioVenta", "6,1,1,600");
        jdbc.update("UPDATE lista_precio_articulo SET fechaBaja=CURRENT_TIMESTAMP WHERE id=6");
    }

    private void auditada(String tabla, String columnas, String valores) {
        jdbc.update("INSERT INTO " + tabla + " (" + columnas + ",fechaAlta,fechaModificacion,usuario_carga_id,usuario_modificacion_id) VALUES (" + valores + ",CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,1,1)");
    }

    @Test
    void clientesActivosPaginadosConOrdenEstable() throws Exception {
        mvc.perform(get("/api/catalogos/clientes/explorer").param("tamanio", "1").param("pagina", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contenido", hasSize(1)))
                .andExpect(jsonPath("$.contenido[0].id").value(2)).andExpect(jsonPath("$.contenido[0].cuitCuil").value("20222"))
                .andExpect(jsonPath("$.totalElementos").value(3)).andExpect(jsonPath("$.totalPaginas").value(3));
    }

    @ParameterizedTest
    @CsvSource({"ana,2", "20222,1", "%,1", "_,1", "inexistente,0"})
    void buscaClientesPorNombreODocumentoLiteral(String termino, int total) throws Exception {
        mvc.perform(get("/api/catalogos/clientes/explorer").param("busqueda", termino))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contenido", hasSize(total)))
                .andExpect(jsonPath("$.totalElementos").value(total));
    }

    @ParameterizedTest
    @CsvSource({"ART-1,2", "LAPIZ,2", "MAY,2", "minorista,1", "%,1", "_,1", "inexistente,0"})
    void buscaLpaActivasPorArticuloOLista(String termino, int total) throws Exception {
        mvc.perform(get("/api/catalogos/listas-precio-articulos/explorer").param("busqueda", termino))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contenido", hasSize(total)))
                .andExpect(jsonPath("$.totalElementos").value(total));
    }

    @Test
    void lpaIncluyeIdentidadCompletaYPrecioConPaginacion() throws Exception {
        mvc.perform(get("/api/catalogos/listas-precio-articulos/explorer").param("busqueda", "ART-1").param("tamanio", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contenido[0].id").value(1))
                .andExpect(jsonPath("$.contenido[0].articuloId").value(1))
                .andExpect(jsonPath("$.contenido[0].articuloCodigo").value("ART-1"))
                .andExpect(jsonPath("$.contenido[0].articuloDenominacion").value("Lapiz"))
                .andExpect(jsonPath("$.contenido[0].listaPrecioCodigo").value("MAY"))
                .andExpect(jsonPath("$.contenido[0].listaPrecioDenominacion").value("Mayorista"))
                .andExpect(jsonPath("$.contenido[0].precioVenta").value(100))
                .andExpect(jsonPath("$.totalElementos").value(2)).andExpect(jsonPath("$.totalPaginas").value(2));
        mvc.perform(get("/api/catalogos/listas-precio-articulos/explorer").param("pagina", "99"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contenido", empty()))
                .andExpect(jsonPath("$.tamanio").value(20));
    }

    @ParameterizedTest
    @ValueSource(strings = {"pagina=-1", "tamanio=0", "tamanio=101", "pagina=mal"})
    void validaPaginacionEnAmbosExplorers(String valor) throws Exception {
        String[] partes = valor.split("=");
        for (String recurso : new String[]{"clientes", "listas-precio-articulos"}) {
            mvc.perform(get("/api/catalogos/" + recurso + "/explorer").param(partes[0], partes[1]))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        }
    }

    @Test
    void facturaUsaLpaElegidaYPrecioActualSinConfiarEnPrecioCliente() throws Exception {
        jdbc.update("UPDATE lista_precio_articulo SET precioVenta=125 WHERE id=1");
        mvc.perform(post("/api/facturas").contentType(MediaType.APPLICATION_JSON).content(alta(1L, 1, ",\"precioUnitario\":1")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.importeTotal").value(250));
        assertEquals(1L, jdbc.queryForObject("SELECT lista_precio_articulo_id FROM factura_venta_detalle", Long.class));
        assertEquals(125D, jdbc.queryForObject("SELECT precioUnitario FROM factura_venta_detalle", Double.class));
    }

    @Test
    void omitirLpaSeleccionaUltimoPrecioConArticuloYListaActivos() throws Exception {
        // El LPA id5 pertenece a una lista de baja: se elige id2, que mantiene las tres entidades activas.
        mvc.perform(post("/api/facturas").contentType(MediaType.APPLICATION_JSON).content(alta(null, 1, "")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.importeTotal").value(400));
        assertEquals(2L, jdbc.queryForObject("SELECT lista_precio_articulo_id FROM factura_venta_detalle", Long.class));
    }

    @ParameterizedTest
    @CsvSource({"1,2,400", "4,3,400", "5,1,400", "6,1,400", "999,1,404", "0,1,400", "-1,1,400"})
    void rechazaLpaAjenaInactivaInexistenteONoPositiva(long lpa, long articulo, int status) throws Exception {
        mvc.perform(post("/api/facturas").contentType(MediaType.APPLICATION_JSON).content(alta(lpa, articulo, "")))
                .andExpect(status().is(status)).andExpect(jsonPath("$.status").value(status));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM factura_venta", Integer.class));
    }

    private String alta(Long lpa, long articulo, String extras) {
        return "{\"puntoVentaId\":1,\"clienteId\":1,\"condicionIvaId\":1,\"tipoMonedaId\":1,\"detalles\":[{\"articuloId\":" + articulo
                + ",\"cantidad\":2" + (lpa == null ? "" : ",\"listaPrecioArticuloId\":" + lpa) + extras + "}]}";
    }
}
