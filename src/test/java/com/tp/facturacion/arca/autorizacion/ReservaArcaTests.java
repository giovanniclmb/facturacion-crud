package com.tp.facturacion.arca.autorizacion;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import com.tp.facturacion.exception.ReglaNegocioException;
import com.tp.facturacion.service.FacturaService;
import com.tp.facturacion.dto.FacturaRequestDto;
import static org.junit.jupiter.api.Assertions.*;
import static com.tp.facturacion.arca.autorizacion.ContratoFiscal.*;
import static com.tp.facturacion.arca.autorizacion.EstadoIntentoArca.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:reserva-arca;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.show-sql=false","facturacion.usuario-sistema-id=999","arca.habilitado=true"})
class ReservaArcaTests {
 @Autowired JdbcTemplate jdbc;
 @Autowired AlmacenIntentosArca almacen;
 @Autowired FacturaService facturas;
 @Autowired org.springframework.transaction.PlatformTransactionManager tx;
 @Autowired com.tp.facturacion.repository.FacturaVentaRepository facturasRepo;
 final Serie serie=new Serie("HOMOLOGACION","20123456786",1,11);
    @BeforeEach
    void preparar() {

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
    }

    private void auditada(String tabla, String columnas, String valores) {
        jdbc.update("INSERT INTO " + tabla + " (" + columnas + ",fechaAlta,fechaModificacion,usuario_carga_id,usuario_modificacion_id) VALUES (" + valores + ",TIMESTAMP '2026-10-01 12:00:00',TIMESTAMP '2026-10-01 12:00:00',1,1)");
    }


 @Test void reservaDurableImpideMutarYRepetirEnvio() {
  Reserva r=almacen.reservar(1,serie,Set.of(6),true);
  assertTrue(r.creada());
  assertFalse(almacen.reservar(1,serie,Set.of(6),true).creada());
  assertThrows(ReglaNegocioException.class,()->facturas.actualizar(1L,new FacturaRequestDto()));
  assertThrows(ReglaNegocioException.class,()->facturas.eliminar(1L));
  assertTrue(almacen.comenzarEnvio(r,1).isPresent());
  assertTrue(almacen.comenzarEnvio(r,1).isEmpty());
  almacen.fallarAntesEnvio(r);
  assertEquals(ENVIANDO,almacen.estado(1).estadoFiscal());
 }
 @Test void recuperarPreparacionInvalidaPropietarioViejo() {
  Reserva viejo=almacen.reservar(1,serie,Set.of(6),true);
  Reserva nuevo=almacen.recuperarPreparando(1);
  assertNotEquals(viejo.propietario(),nuevo.propietario());
  assertTrue(almacen.comenzarEnvio(viejo,1).isEmpty());
  almacen.fallarAntesEnvio(viejo);
  assertEquals(PREPARANDO,almacen.estado(1).estadoFiscal());
  assertTrue(almacen.comenzarEnvio(nuevo,1).isPresent());
 }
 @Test void rechazoPermiteCorregirYReutilizarNumeroConHistoria() {
  Reserva r=almacen.reservar(1,serie,Set.of(6),true);
  almacen.comenzarEnvio(r,1);
  almacen.finalizar(r.intento(),new ResultadoEnvio(false,null,null,List.of(new ErrorFiscal(1,"Rechazo confirmado")),List.of()));
  assertTrue(almacen.estado(1).puedeModificar());
  assertThrows(ReglaNegocioException.class,()->facturas.eliminar(1L));
  Reserva nuevo=almacen.reservar(1,serie,Set.of(6),true);
  assertNotEquals(r.intento(),nuevo.intento());
  assertTrue(almacen.comenzarEnvio(nuevo,1).isPresent());
  assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM arca_intento",Integer.class));
 }
 @Test void consultaDiferenteNoAdoptaCaeYCoincidenciaDecimalSi() {
  Reserva r=almacen.reservar(1,serie,Set.of(6),true);
  Envio e=almacen.comenzarEnvio(r,1).orElseThrow();
  Snapshot s=e.snapshot();
  almacen.recuperar(r.intento(),new Comprobante(serie,1,new Snapshot(s.concepto(),s.docTipo(),s.docNumero()+1,s.condicionIva(),s.fecha(),s.moneda(),s.cotizacion(),s.total()),"12345678901234",LocalDate.now().plusDays(10)));
  assertEquals(INCIERTO,almacen.estado(1).estadoFiscal());assertNull(almacen.estado(1).cae());
  almacen.recuperar(r.intento(),new Comprobante(serie,1,new Snapshot(s.concepto(),s.docTipo(),s.docNumero(),s.condicionIva(),s.fecha(),s.moneda(),new BigDecimal("1.000000"),new BigDecimal("200.000")),"12345678901234",LocalDate.now().plusDays(10)));
  assertEquals(AUTORIZADO,almacen.estado(1).estadoFiscal());assertEquals(7L,almacen.estado(1).numeroLocal());assertEquals(1L,almacen.estado(1).numeroFiscal());
  almacen.finalizar(r.intento(),new ResultadoEnvio(false,null,null,List.of(),List.of()));
  assertEquals(AUTORIZADO,almacen.estado(1).estadoFiscal());
 }
 @Test void emitidaSinCaeNoPermiteAutorizar() {
  jdbc.update("UPDATE factura_venta SET estado='EMITIDA' WHERE id=1");
  assertFalse(almacen.estado(1).puedeAutorizar());
  assertThrows(ReglaNegocioException.class,()->almacen.reservar(1,serie,Set.of(6),true));
 }
 @Test void reservaEsperaEdicionYGuardaSnapshotFresco() throws Exception {
  var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
  var bloqueada=new java.util.concurrent.CountDownLatch(1);
  var reservarListo=new java.util.concurrent.CountDownLatch(1);
  var liberar=new java.util.concurrent.CountDownLatch(1);
  try {
   var edicion=pool.submit(()->new org.springframework.transaction.support.TransactionTemplate(tx).execute(status->{
    var f=facturasRepo.buscarBloqueada(1L).orElseThrow();bloqueada.countDown();
    try {assertTrue(liberar.await(10,java.util.concurrent.TimeUnit.SECONDS));}catch(InterruptedException e){throw new RuntimeException(e);}
    f.setImporteTotal(300);f.getDetalles().getFirst().setPrecioUnitario(150);
    f.getDetalles().getFirst().setImporteNeto(300);f.getDetalles().getFirst().setImporteSubtotal(300);return null;
   }));
   assertTrue(bloqueada.await(10,java.util.concurrent.TimeUnit.SECONDS));
   var reserva=pool.submit(()->{reservarListo.countDown();return almacen.reservar(1,serie,Set.of(6),true);});
   assertTrue(reservarListo.await(10,java.util.concurrent.TimeUnit.SECONDS));liberar.countDown();
   edicion.get(10,java.util.concurrent.TimeUnit.SECONDS);
   var envio=almacen.comenzarEnvio(reserva.get(10,java.util.concurrent.TimeUnit.SECONDS),1).orElseThrow();
   assertEquals(0,new BigDecimal("300").compareTo(envio.snapshot().total()));
  }finally{liberar.countDown();pool.shutdownNow();}
 }
 @Test void seriesDistintasMantienenReservasIndependientes() {
  auditada("punto_venta","id,numero,descripcion","2,2,'Otro punto'");
  auditada("factura_venta", "id,numero,fechaEmision,cliente_id,condicion_iva_id,punto_venta_id,moneda_id,importeTotal,importeCobrado,importeSaldo,estado",
   "2,8,CURRENT_TIMESTAMP,1,1,2,1,200,0,200,'PENDIENTE'");
  jdbc.update("INSERT INTO factura_venta_detalle(id,factura_id,lista_precio_articulo_id,cantidad,precioUnitario,porcentajeBonificacion,importeNeto,importeIva,importeSubtotal) VALUES(2,2,1,2,100,0,200,0,200)");
  assertTrue(almacen.reservar(1,serie,Set.of(6),true).creada());
  assertTrue(almacen.reservar(2,new Serie(serie.ambiente(),serie.emisor(),2,11),Set.of(6),true).creada());
 }

}
