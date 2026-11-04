package com.tp.facturacion.arca.autorizacion;
import java.sql.*;
import java.nio.file.*;
import java.util.UUID;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
/** Cada instancia posee exclusivamente un esquema aleatorio de una base de pruebas. */
abstract class PostgresArcaFixture {
 String url,user,password,schema;
 @BeforeEach void crearEsquema() throws Exception {
  url=System.getenv("ARCA_TEST_JDBC_URL");
  assertNotNull(url,"El perfil arca-postgres exige ARCA_TEST_JDBC_URL de una base de pruebas");
  assertTrue(url.startsWith("jdbc:postgresql:"),"ARCA_TEST_JDBC_URL debe ser PostgreSQL");
  user=System.getenv().getOrDefault("ARCA_TEST_JDBC_USER","");password=System.getenv().getOrDefault("ARCA_TEST_JDBC_PASSWORD","");
  schema="arca_test_"+UUID.randomUUID().toString().replace("-","");
  try(Connection c=abrirBase()){c.createStatement().execute("CREATE SCHEMA "+schema);}
  try(Connection c=abrir()){
   String base=Files.readString(Path.of("src/test/resources/arca/autorizacion/base-heredada.sql"));
   c.createStatement().execute(base);migrar(c);
  }
 }
 Connection abrirBase() throws SQLException {return DriverManager.getConnection(url,user,password);}
 Connection abrir() throws SQLException {Connection c=abrirBase();c.createStatement().execute("SET search_path TO "+schema);return c;}
 void migrar(Connection c) throws Exception {
  c.setAutoCommit(false);
  try {String sql=Files.readString(Path.of("db/migrations/001_arca_autorizacion.sql"));
   sql=sql.replace("BEGIN;", "").replace("COMMIT;", "");c.createStatement().execute(sql);c.commit();
  }catch(Exception e){c.rollback();throw e;}finally{c.setAutoCommit(true);}
 }
 @AfterEach void limpiar() throws Exception {
  if(schema==null||url==null)return;
  assertTrue(schema.matches("arca_test_[a-f0-9]{32}"));
  try(Connection c=abrirBase()){c.createStatement().execute("DROP SCHEMA "+schema+" CASCADE");}
 }
 UUID insertarIntento(Connection c,int factura,long serie,String estado,Long numero) throws SQLException {
  UUID id=UUID.randomUUID();
  try(PreparedStatement s=c.prepareStatement("INSERT INTO arca_intento(id,factura_id,serie_id,usuario_iniciador_id,propietario,estado,numero,concepto,doc_tipo,doc_numero,condicion_iva,fecha,moneda,cotizacion,total,hash,creado,actualizado,errores,observaciones,version) VALUES(?,?,?,1,?,?,?,1,80,30712345671,6,CURRENT_DATE,'PES',1,200,'fixture',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'','',0)")) {
   s.setObject(1,id);s.setInt(2,factura);s.setLong(3,serie);s.setObject(4,UUID.randomUUID());s.setString(5,estado);s.setObject(6,numero);s.executeUpdate();
  }return id;
 }
 long serie(Connection c) throws SQLException {
  try(ResultSet r=c.createStatement().executeQuery("INSERT INTO arca_serie(ambiente,emisor,punto_venta,tipo) VALUES('HOMOLOGACION','20123456786',1,11) RETURNING id")){r.next();return r.getLong(1);}
 }
 long count(Connection c,String sql) throws SQLException {try(ResultSet r=c.createStatement().executeQuery(sql)){r.next();return r.getLong(1);}}
}
