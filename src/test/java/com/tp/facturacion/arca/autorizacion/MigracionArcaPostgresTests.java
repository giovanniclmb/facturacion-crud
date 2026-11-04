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
