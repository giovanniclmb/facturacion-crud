package com.tp.facturacion.arca.autorizacion;
import java.sql.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;
@EnabledIfSystemProperty(named="arca.postgres",matches="true")
class MigracionArcaPostgresTests extends PostgresArcaFixture {
 @Test void conservaDatosYRerunRevierte() throws Exception {
  try(Connection c=abrir()) {
   assertEquals(7,count(c,"SELECT numero FROM factura_venta WHERE id=1"));
   assertEquals(5,count(c,"SELECT count(*) FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='factura_venta' AND column_name LIKE '%fiscal'"));
   assertThrows(SQLException.class,()->migrar(c));
   assertEquals(2,count(c,"SELECT count(*) FROM factura_venta"));
   assertEquals(1,count(c,"SELECT count(*) FROM pg_indexes WHERE schemaname=current_schema() AND indexname='uq_arca_factura_activa'"));
  }
 }
 @Test void validaClienteYDocumentoSegunElTipo() throws Exception {
  try (Connection c=abrir()) {
   assertThrows(SQLException.class, ()->c.createStatement().executeUpdate(
           "UPDATE factura_venta SET cliente_id=NULL WHERE id=1"));
   assertEquals(1, c.createStatement().executeUpdate(
           "UPDATE factura_venta SET cliente_id=NULL,tipo_comprobante='FACTURA_X' WHERE id=1"));
   assertThrows(SQLException.class, ()->c.createStatement().executeUpdate(
           "UPDATE factura_venta SET cae='12345678901234' WHERE id=1"));
   assertThrows(SQLException.class, ()->c.createStatement().executeUpdate(
           "UPDATE factura_venta SET documento_receptor_tipo=96,documento_receptor_numero=12345678 WHERE id=1"));
   assertEquals(1, c.createStatement().executeUpdate(
           "UPDATE factura_venta SET tipo_comprobante='FACTURA_B',documento_receptor_tipo=96,"
           + "documento_receptor_numero=12345678 WHERE id=1"));
  }
 }
 @Test void cargaClientesDeDemoSinDuplicarlosNiModificarFacturas() throws Exception {
  try (Connection c=abrir(); Statement sql=c.createStatement()) {
   for (String tabla : java.util.List.of("cliente", "contacto", "domicilio")) {
    sql.execute("ALTER TABLE " + tabla + " ALTER COLUMN id RESTART WITH 100");
   }
   String carga = java.nio.file.Files.readString(java.nio.file.Path.of("db/demo/clientes_arca.sql"));
   sql.execute(carga);
   sql.execute(carga);
   assertEquals(5,count(c,"""
     SELECT count(*) FROM cliente WHERE cuitcuil = '30000000007' AND fechabaja IS NULL
       AND denominacion IN ('Cliente demostración ARCA', 'Giovanni Colombo (demo)', 'Distribuidora Mendoza SA', 'Juan Lopez', 'Maria Gomez')
     """));
   assertEquals(6,count(c,"SELECT count(*) FROM cliente"));
   assertEquals(6,count(c,"SELECT count(*) FROM contacto"));
   assertEquals(6,count(c,"SELECT count(*) FROM domicilio"));
   assertEquals(2,count(c,"SELECT count(*) FROM factura_venta"));
   assertEquals(0,count(c,"SELECT count(*) FROM arca_intento"));
  }
 }
 @Test void restriccionesActivasYRechazosHistoricos() throws Exception {
  try(Connection c=abrir()) {
   long serie=serie(c);insertarIntento(c,1,serie,"RECHAZADO",1L);insertarIntento(c,1,serie,"RECHAZADO",1L);
   insertarIntento(c,1,serie,"ENVIANDO",1L);
   assertThrows(SQLException.class,()->insertarIntento(c,1,serie,"PREPARANDO",null));
   assertThrows(SQLException.class,()->insertarIntento(c,2,serie,"ENVIANDO",1L));
   assertThrows(SQLException.class,()->c.createStatement().executeUpdate("DELETE FROM factura_venta WHERE id=1"));
   assertEquals(3,count(c,"SELECT count(*) FROM arca_intento"));
  }
 }
}
