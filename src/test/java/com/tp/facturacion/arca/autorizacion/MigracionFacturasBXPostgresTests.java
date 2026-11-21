package com.tp.facturacion.arca.autorizacion;

import java.sql.Connection;
import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "arca.postgres", matches = "true")
class MigracionFacturasBXPostgresTests extends PostgresArcaFixture {

    @Override
    void migrar(Connection conexion) throws Exception {
        migrar(conexion, "001_arca_autorizacion.sql", "002_correcciones_facturacion.sql");
    }

    @Test
    void reclasificaLaBInternaYConservaSusDatosYLosIntentosDeFacturaC() throws Exception {
        try (var conexion = abrir(); var sql = conexion.createStatement()) {
            sql.executeUpdate("""
                    UPDATE factura_venta
                    SET cliente_id=NULL, tipo_comprobante='CONSUMIDOR_FINAL', estado='ANULADA'
                    WHERE id=1
                    """);
            sql.executeUpdate("UPDATE factura_venta SET tipo_comprobante='FACTURA_C' WHERE id=2");
            var serieC = serie(conexion);
            insertarIntento(conexion, 2, serieC, "RECHAZADO", 1L);
            String antes = foto(conexion);

            migrar(conexion, "003_facturas_b_x.sql");

            assertEquals(antes, foto(conexion));
            assertEquals(1, count(conexion, """
                    SELECT count(*) FROM factura_venta
                    WHERE id=1 AND tipo_comprobante='FACTURA_X' AND estado='ANULADA'
                    """));
            assertEquals(1, count(conexion, """
                    SELECT count(*) FROM factura_venta AS f JOIN arca_intento AS i ON i.factura_id=f.id
                    WHERE f.id=2 AND f.tipo_comprobante='FACTURA_C' AND i.estado='RECHAZADO' AND i.doc_tipo=80
                    """));
            assertEquals(1, sql.executeUpdate("""
                    INSERT INTO arca_serie (ambiente, emisor, punto_venta, tipo)
                    VALUES ('HOMOLOGACION', '20123456786', 1, 6)
                    """));
            assertThrows(SQLException.class, () -> sql.executeUpdate(
                    "UPDATE factura_venta SET tipo_comprobante='CONSUMIDOR_FINAL' WHERE id=1"));
        }
    }

    private String foto(Connection conexion) throws SQLException {
        try (var sql = conexion.createStatement(); var fila = sql.executeQuery("""
                SELECT (to_jsonb(f) - ARRAY['tipo_comprobante','documento_receptor_tipo','documento_receptor_numero'])
                    || jsonb_build_object('detalles', (SELECT jsonb_agg(to_jsonb(d) - 'iva_porcentaje' ORDER BY d.id)
                        FROM factura_venta_detalle AS d WHERE d.factura_id=f.id))
                FROM factura_venta AS f WHERE f.id=1
                """)) {
            assertTrue(fila.next());
            return fila.getString(1);
        }
    }
}
