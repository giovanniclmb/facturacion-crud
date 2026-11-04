package com.tp.facturacion.arca.autorizacion;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import com.tp.facturacion.FacturacionApiApplication;
import com.tp.facturacion.exception.ReglaNegocioException;
import static com.tp.facturacion.arca.autorizacion.ContratoFiscal.*;
import static org.junit.jupiter.api.Assertions.*;
/** API transaccional real, esquema migrado y validado, dos conexiones PostgreSQL. */
@EnabledIfSystemProperty(named="arca.postgres",matches="true")
class ReservaArcaPostgresTests extends PostgresArcaFixture {
 ConfigurableApplicationContext app;
 AlmacenIntentosArca almacen;
 final Serie serie=new Serie("HOMOLOGACION","20123456786",1,11);
 void iniciarValidando() {
  String schemaUrl=url+(url.contains("?")?"&":"?")+"currentSchema="+schema;
  app=new SpringApplicationBuilder(FacturacionApiApplication.class).web(WebApplicationType.NONE).run(
   "--spring.config.import=", "--spring.datasource.url="+schemaUrl,
   "--spring.datasource.username="+user,"--spring.datasource.password="+password,
   "--spring.datasource.driver-class-name=org.postgresql.Driver",
   "--spring.jpa.hibernate.ddl-auto=validate","--spring.jpa.show-sql=false",
   "--facturacion.usuario-sistema-id=1","--arca.habilitado=false");
  almacen=app.getBean(AlmacenIntentosArca.class);
 }
 @AfterEach void cerrarApp(){if(app!=null)app.close();}
 @Test void primeraSerieTieneUnSoloGanadorSinReusarTransaccionAbortada() throws Exception {
  iniciarValidando();
  ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch listas=new CountDownLatch(2),inicio=new CountDownLatch(1);
  try {
   Future<Boolean> a=pool.submit(()->reservar(1,listas,inicio));Future<Boolean> b=pool.submit(()->reservar(2,listas,inicio));
   assertTrue(listas.await(10,TimeUnit.SECONDS));inicio.countDown();
   assertEquals(1,(a.get(15,TimeUnit.SECONDS)?1:0)+(b.get(15,TimeUnit.SECONDS)?1:0));
   try(Connection c=abrir()){assertEquals(1,count(c,"SELECT count(*) FROM arca_serie"));assertEquals(1,count(c,"SELECT count(*) FROM arca_intento WHERE estado='PREPARANDO'"));}
  }finally{inicio.countDown();pool.shutdownNow();}
 }
 boolean reservar(long factura,CountDownLatch listas,CountDownLatch inicio) throws Exception {
  listas.countDown();assertTrue(inicio.await(10,TimeUnit.SECONDS));
  try{return almacen.reservar(factura,serie,Set.of(6),true).creada();}
  catch(ReglaNegocioException esperado){return false;}
 }
 @Test void propietarioAnteriorNoPuedeEnviarYRechazoConservaHistorial() {
  iniciarValidando();
  Reserva original=almacen.reservar(1,serie,Set.of(6),true);
  Reserva nuevaPropiedad=almacen.recuperarPreparando(1);
  assertTrue(almacen.comenzarEnvio(original,1).isEmpty());
  assertTrue(almacen.comenzarEnvio(nuevaPropiedad,1).isPresent());
  almacen.finalizar(original.intento(),new ResultadoEnvio(false,null,null,List.of(new ErrorFiscal(1,"Rechazo")),List.of()));
  assertTrue(almacen.estado(1).puedeModificar());
  assertThrows(ReglaNegocioException.class,()->app.getBean(com.tp.facturacion.service.FacturaService.class).eliminar(1L));
  Reserva otra=almacen.reservar(1,serie,Set.of(6),true);
  assertNotEquals(original.intento(),otra.intento());
  assertTrue(almacen.comenzarEnvio(otra,1).isPresent());
 }
}
