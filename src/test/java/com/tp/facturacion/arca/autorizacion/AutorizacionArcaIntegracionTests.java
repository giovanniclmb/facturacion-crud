package com.tp.facturacion.arca.autorizacion;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.math.BigDecimal;
import com.tp.facturacion.arca.*;
import com.tp.facturacion.dto.FacturaRequestDto;
import com.tp.facturacion.service.FacturaService;
import com.tp.facturacion.exception.ReglaNegocioException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.tp.facturacion.arca.autorizacion.ContratoFiscal.*;
import static com.tp.facturacion.arca.autorizacion.EstadoIntentoArca.*;

@SpringBootTest(properties={"spring.config.import=", "spring.datasource.url=jdbc:h2:mem:arca-circuito;DB_CLOSE_DELAY=-1",
 "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
 "spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.show-sql=false","facturacion.usuario-sistema-id=999",
 "arca.habilitado=true","arca.cuit=20123456786","arca.certificado=/ficticio/cert","arca.clave-privada=/ficticio/key",
 "arca.ticket-cache=/ficticio/cache","arca.puntos-venta-exclusivos=1"})
@Import(AutorizacionArcaIntegracionTests.Remotos.class)
class AutorizacionArcaIntegracionTests {
 @TestConfiguration static class Remotos {
  @Bean @Primary WsaaClient wsaaPrueba(){return mock(WsaaClient.class);}
  @Bean @Primary WsfeClient lecturasPrueba(){return mock(WsfeClient.class);}
  @Bean @Primary WsfeAutorizacionClient autorizacionPrueba(){return mock(WsfeAutorizacionClient.class);}
 }
 @Autowired JdbcTemplate jdbc;
 @Autowired AutorizacionArcaService servicio;
 @Autowired AlmacenIntentosArca almacen;
 @Autowired FacturaService facturas;
 @Autowired WsaaClient wsaa;
 @Autowired WsfeClient lecturas;
 @Autowired WsfeAutorizacionClient cliente;
 @Autowired ArcaProperties props;
 AtomicReference<Envio> enviado=new AtomicReference<>();
 @BeforeEach void preparar() {
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
        auditada("factura_venta", "id,numero,fechaEmision,cliente_id,condicion_iva_id,punto_venta_id,moneda_id,importeTotal,importeCobrado,importeSaldo,estado,cae,observaciones,resultadoAfip,caeFechaVencimiento",
                "1,7,TIMESTAMP '2026-10-01 12:00:00',1,1,1,1,200,50,150,'EMITIDA',' 12345 ','Original','A',TIMESTAMP '2026-10-10 00:00:00'");
        jdbc.update("INSERT INTO factura_venta_detalle (id,factura_id,lista_precio_articulo_id,descripcion,cantidad,precioUnitario,porcentajeBonificacion,importeNeto,importeIva,importeSubtotal) VALUES (1,1,1,'Original',2,100,0,200,0,200)");
        jdbc.update("UPDATE factura_venta SET cae=null,estado='PENDIENTE'");
        jdbc.update("UPDATE cliente SET cuitCuil='30712345671'");
        jdbc.update("UPDATE condicion_iva SET codigoAfip=6");
        jdbc.update("UPDATE factura_venta SET fechaEmision=CURRENT_TIMESTAMP");

  reset(wsaa,lecturas,cliente);
  var ticket=new TicketAcceso("TOKEN_SINTETICO","SIGN_SINTETICO",Instant.now().plusSeconds(7200));
  when(wsaa.obtenerTicket()).thenReturn(ticket);
  when(lecturas.condicionesIvaReceptor(any())).thenReturn(List.of(new CondicionIvaReceptor(6,"Monotributo","A/ALEY/C")));
  when(lecturas.ultimoAutorizado(any(),anyInt(),anyInt())).thenReturn(0L);
  when(cliente.autorizar(any(),any())).thenAnswer(inv->{enviado.set(inv.getArgument(1));return aceptacion();});
 }
 private ResultadoEnvio aceptacion(){return new ResultadoEnvio(true,"12345678901234",LocalDate.now().plusDays(10),List.of(),List.of());}
 private void auditada(String tabla,String columnas,String valores){
  jdbc.update("INSERT INTO "+tabla+" ("+columnas+",fechaAlta,fechaModificacion,usuario_carga_id,usuario_modificacion_id) VALUES ("+valores+",CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,1,1)");
 }
 @Test void autorizacionPersisteNumeroFiscalCaeYBloqueaFactura() {
  var resultado=servicio.autorizar(1,true);
  assertEquals(AUTORIZADO,resultado.estadoFiscal());assertEquals(1L,resultado.numeroFiscal());assertEquals(7L,resultado.numeroLocal());
  var reporte=facturas.obtenerReporte(1L);
  assertEquals("EMITIDA",reporte.getEstado());assertEquals(AUTORIZADO,reporte.getEstadoFiscal());
  assertEquals("12345678901234",reporte.getCae());assertFalse(reporte.isPuedeModificar());
  assertThrows(ReglaNegocioException.class,()->facturas.actualizar(1L,new FacturaRequestDto()));
  assertThrows(ReglaNegocioException.class,()->facturas.eliminar(1L));
  servicio.autorizar(1,true);verify(cliente,times(1)).autorizar(any(),any());
 }
 @Test void timeoutSeRecuperaDesdePersistenciaSinNuevoEnvio() {
  when(cliente.autorizar(any(),any())).thenThrow(new ArcaRemotaException());
  assertEquals(INCIERTO,servicio.autorizar(1,true).estadoFiscal());
  var envio=almacen.envioPendiente(1).orElseThrow();
  when(cliente.consultar(any(),eq(envio.serie()),eq(envio.numero())))
   .thenReturn(new Consulta(Optional.of(new Comprobante(envio.serie(),envio.numero(),envio.snapshot(),aceptacion().cae(),aceptacion().vencimiento()))));
  var otraInstancia=new AutorizacionArcaService(props,wsaa,lecturas,cliente,almacen);
  assertEquals(INCIERTO,otraInstancia.autorizar(1,true).estadoFiscal());
  assertEquals(AUTORIZADO,otraInstancia.consultar(1).estadoFiscal());
  verify(cliente,times(1)).autorizar(any(),any());
 }
 @Test void rechazoPermiteNuevoIntentoSinPerderHistoria() {
  when(cliente.autorizar(any(),any())).thenReturn(new ResultadoEnvio(false,null,null,List.of(new ErrorFiscal(100,"Rechazo de prueba")),List.of()));
  assertEquals(RECHAZADO,servicio.autorizar(1,true).estadoFiscal());
  assertTrue(almacen.estado(1).puedeModificar());
  assertThrows(ReglaNegocioException.class,()->facturas.eliminar(1L));
  when(cliente.autorizar(any(),any())).thenReturn(aceptacion());
  assertEquals(AUTORIZADO,servicio.autorizar(1,true).estadoFiscal());
  assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM arca_intento",Integer.class));
 }
 @Test void colisionDeComprobanteNoAdoptaCaeAjeno() {
  when(cliente.autorizar(any(),any())).thenThrow(new ArcaRemotaException());
  servicio.autorizar(1,true);
  var envio=almacen.envioPendiente(1).orElseThrow();var s=envio.snapshot();
  var distinto=new Snapshot(s.concepto(),s.docTipo(),s.docNumero(),s.condicionIva(),s.fecha(),s.moneda(),s.cotizacion(),new BigDecimal("999.00"));
  when(cliente.consultar(any(),any(),anyLong())).thenReturn(new Consulta(Optional.of(new Comprobante(envio.serie(),envio.numero(),distinto,aceptacion().cae(),aceptacion().vencimiento()))));
  var r=servicio.consultar(1);
  assertEquals(INCIERTO,r.estadoFiscal());assertNull(r.cae());assertFalse(r.puedeModificar());
  servicio.autorizar(1,true);verify(cliente,times(1)).autorizar(any(),any());
 }
}
