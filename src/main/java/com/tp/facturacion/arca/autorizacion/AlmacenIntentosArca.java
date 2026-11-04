package com.tp.facturacion.arca.autorizacion;

import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.tp.facturacion.arca.ArcaProperties;
import com.tp.facturacion.dto.FacturaArcaDto;
import com.tp.facturacion.exception.*;
import com.tp.facturacion.model.FacturaVenta;
import com.tp.facturacion.repository.FacturaVentaRepository;
import static com.tp.facturacion.arca.autorizacion.ContratoFiscal.*;
import static com.tp.facturacion.arca.autorizacion.EstadoIntentoArca.*;

/** Exclusivamente persistencia: ninguna operación invoca servicios remotos. */
@Service
@Transactional
public class AlmacenIntentosArca {
 private final IntentoArcaRepository intentos;
 private final SerieArcaRepository series;
 private final FacturaVentaRepository facturas;
 private final EntityManager em;
 private final SnapshotFiscalFactory factory;
 private final ArcaProperties props;
 private final Clock reloj;
 @org.springframework.beans.factory.annotation.Value("${facturacion.usuario-sistema-id}")
 private long usuarioSistemaId;
 public AlmacenIntentosArca(IntentoArcaRepository i,SerieArcaRepository s,FacturaVentaRepository f,
   EntityManager em,ArcaProperties p,Clock reloj) {
  this.intentos=i;this.series=s;this.facturas=f;this.em=em;this.props=p;this.reloj=reloj;
  this.factory=new SnapshotFiscalFactory(reloj);
 }
 public Reserva reservar(long facturaId,Serie serie,Set<Integer> iva,boolean confirmar) {
  SerieArca s=crearYBloquear(serie);
  FacturaVenta f=facturaBloqueada(facturaId);
  var anterior=ultimo(facturaId);
  if(anterior.isPresent() && IntentoArca.ESTADOS_ACTIVOS.contains(anterior.get().estado)) {
   var i=anterior.get();return new Reserva(i.id,i.propietario,false);
  }
  if(s.intentoActivo!=null) throw conflicto("La serie tiene una autorización pendiente");
  if(tieneCae(f)||!"PENDIENTE".equals(f.getEstado())) throw conflicto("La factura no permite autorización");
  if(f.getPuntoVenta().getNumero()!=serie.puntoVenta()) throw conflicto("El punto de venta cambió");
  Snapshot snapshot=factory.crear(f,serie.emisor(),iva,confirmar);
  IntentoArca i=new IntentoArca();i.id=UUID.randomUUID();i.propietario=UUID.randomUUID();
  i.factura=f;i.serie=s;i.usuarioIniciador=em.getReference(com.tp.facturacion.model.Usuario.class,usuarioSistemaId);i.estado=PREPARANDO;i.snapshot(snapshot);i.hash=factory.hash(snapshot);
  i.creado=reloj.instant();i.actualizado=i.creado;
  intentos.saveAndFlush(i);s.intentoActivo=i.id;
  return new Reserva(i.id,i.propietario,true);
 }
 public Optional<Envio> comenzarEnvio(Reserva reserva,long numero) {
  IntentoArca i=bloquear(reserva.intento());
  if(i.estado!=PREPARANDO||!i.id.equals(i.serie.intentoActivo)||!i.propietario.equals(reserva.propietario())) return Optional.empty();
  if(numero<=0) throw new IllegalArgumentException("Número fiscal inválido");
  i.numero=numero;i.estado=ENVIANDO;tocar(i);return Optional.of(envio(i));
 }
 public void fallarAntesEnvio(Reserva reserva) {
  IntentoArca i=bloquear(reserva.intento());
  if(i.estado==PREPARANDO&&i.propietario.equals(reserva.propietario())) {
   i.estado=FALLIDO_ANTES_ENVIO;liberar(i);tocar(i);
  }
 }
 public void marcarIncierto(UUID id) {
  IntentoArca i=bloquear(id);if(i.estado==ENVIANDO){i.estado=INCIERTO;tocar(i);}
 }
 public void finalizar(UUID id,ResultadoEnvio resultado) {
  IntentoArca i=bloquear(id);
  if(i.estado==AUTORIZADO||i.estado==RECHAZADO) return;
  if((i.estado!=ENVIANDO&&i.estado!=INCIERTO)||!i.id.equals(i.serie.intentoActivo)) throw conflicto("El intento no fue enviado o perdió su reserva");
  if(resultado.aceptado()) autorizar(i,resultado.cae(),resultado.vencimiento());
  else {i.estado=RECHAZADO;i.factura.setResultadoAfip("R");i.factura.setMotivoRechazo(resultado.errores().stream().map(e->sanear(e.mensaje())).collect(java.util.stream.Collectors.joining("; ")));liberar(i);}
  auditar(i.factura);
  i.errores=codificar(resultado.errores());i.observaciones=codificar(resultado.observaciones());tocar(i);
 }
 public void recuperar(UUID id,Comprobante c) {
  IntentoArca i=bloquear(id);
  if(i.estado==AUTORIZADO) return;
  if((i.estado!=ENVIANDO&&i.estado!=INCIERTO)||!i.id.equals(i.serie.intentoActivo)) throw conflicto("El intento no requiere consulta");
  if(!i.serie.serie().equals(c.serie())||i.numero==null||i.numero!=c.numero()||!coincide(i.getSnapshot(),c.snapshot())) {
   i.estado=INCIERTO;i.errores=codificar(List.of(new ErrorFiscal(0,"El comprobante consultado no coincide con el intento")));tocar(i);return;
  }
  autorizar(i,c.cae(),c.vencimiento());tocar(i);
 }
 public Reserva recuperarPreparando(long facturaId) {
  IntentoArca previo=ultimo(facturaId).orElseThrow(()->conflicto("No existe intento pendiente"));
  IntentoArca i=bloquear(previo.id);
  if(i.estado!=PREPARANDO||i.numero!=null||!i.id.equals(i.serie.intentoActivo)) throw conflicto("El intento ya no está en preparación");
  i.propietario=UUID.randomUUID();tocar(i);return new Reserva(i.id,i.propietario,true);
 }
 @Transactional(readOnly=true)
 public Optional<Envio> envioPendiente(long facturaId) {
  return ultimo(facturaId).filter(i->i.numero!=null&&(i.estado==ENVIANDO||i.estado==INCIERTO)).map(this::envio);
 }
 @Transactional(readOnly=true)
 public FacturaArcaDto estado(long facturaId) {
  FacturaVenta f=facturas.findById(facturaId).orElseThrow(()->new RecursoNoEncontradoException("No existe la factura"));
  IntentoArca i=ultimo(facturaId).orElse(null);
  boolean bloqueada=tieneCae(f)||(i!=null&&IntentoArca.ESTADOS_ACTIVOS.contains(i.estado));
  boolean anulada="ANULADA".equals(f.getEstado());
  boolean consultar=i!=null&&(i.estado==PREPARANDO||i.estado==ENVIANDO||i.estado==INCIERTO);
  LocalDate vencimiento=i==null?(f.getCaeFechaVencimiento()==null?null:Instant.ofEpochMilli(f.getCaeFechaVencimiento().getTime()).atZone(ZoneId.of("America/Argentina/Buenos_Aires")).toLocalDate()):i.vencimiento;
  return new FacturaArcaDto(facturaId,f.getNumero(),i==null?null:i.id,i==null?null:i.estado,
   i==null?f.getNumeroFiscal():i.numero,i==null?f.getPuntoVenta().getNumero():i.serie.getPuntoVenta(),
   i==null?11:i.serie.getTipo(),i==null?f.getAmbienteFiscal():i.serie.getAmbiente(),f.getCae(),vencimiento,
   i==null?(props.getPuntosVentaExclusivos().contains(f.getPuntoVenta().getNumero())?"Sin informar":"Configure un punto de venta exclusivo para autorizar"):i.estado.name(),i==null?List.of():decodificar(i.errores),i==null?List.of():decodificar(i.observaciones),
   props.isHabilitado()&&props.getPuntosVentaExclusivos().contains(f.getPuntoVenta().getNumero())&&!bloqueada&&"PENDIENTE".equals(f.getEstado()),consultar,!bloqueada&&!anulada,!bloqueada&&!anulada,!bloqueada&&i==null);
 }
 private SerieArca crearYBloquear(Serie s) {
  boolean pg=em.unwrap(org.hibernate.Session.class).doReturningWork(c->c.getMetaData().getDatabaseProductName().equals("PostgreSQL"));
  if(pg) {
   em.createNativeQuery("SET LOCAL lock_timeout = '3s'").executeUpdate();
   em.createNativeQuery("INSERT INTO arca_serie(ambiente,emisor,punto_venta,tipo) VALUES (?1,?2,?3,?4) ON CONFLICT (ambiente,emisor,punto_venta,tipo) DO NOTHING")
    .setParameter(1,s.ambiente()).setParameter(2,s.emisor()).setParameter(3,s.puntoVenta()).setParameter(4,s.tipo()).executeUpdate();
  } else {
   // H2 funcional; la exclusión de primeras series se verifica aparte contra PostgreSQL.
   em.createNativeQuery("MERGE INTO arca_serie t USING (VALUES (?1,?2,?3,?4)) v(a,e,p,tipo) ON t.ambiente=v.a AND t.emisor=v.e AND t.punto_venta=v.p AND t.tipo=v.tipo WHEN NOT MATCHED THEN INSERT(ambiente,emisor,punto_venta,tipo) VALUES(v.a,v.e,v.p,v.tipo)")
    .setParameter(1,s.ambiente()).setParameter(2,s.emisor()).setParameter(3,s.puntoVenta()).setParameter(4,s.tipo()).executeUpdate();
  }
  return series.bloquear(s.ambiente(),s.emisor(),s.puntoVenta(),s.tipo()).orElseThrow();
 }
 private IntentoArca bloquear(UUID id) {
  boolean pg=em.unwrap(org.hibernate.Session.class).doReturningWork(c->c.getMetaData().getDatabaseProductName().equals("PostgreSQL"));
  if(pg) em.createNativeQuery("SET LOCAL lock_timeout = '3s'").executeUpdate();
  IntentoArca previo=intentos.findById(id).orElseThrow(()->new RecursoNoEncontradoException("No existe intento fiscal"));
  SerieArca serie=series.bloquearId(previo.serie.getId()).orElseThrow();em.refresh(serie);
  FacturaVenta factura=facturaBloqueada(previo.factura.getId());em.refresh(factura);
  em.refresh(previo);return previo;
 }
 private FacturaVenta facturaBloqueada(long id){return facturas.buscarBloqueada(id).orElseThrow(()->new RecursoNoEncontradoException("No existe la factura"));}
 private Optional<IntentoArca> ultimo(long id){return intentos.findFirstByFacturaIdOrderByCreadoDescIdDesc(id);}
 private void tocar(IntentoArca i){i.actualizado=reloj.instant();}
 private void liberar(IntentoArca i){if(i.id.equals(i.serie.intentoActivo))i.serie.intentoActivo=null;}
 private Envio envio(IntentoArca i){return new Envio(i.id,i.serie.serie(),i.numero,i.getSnapshot());}
 private void autorizar(IntentoArca i,String cae,LocalDate vencimiento) {
  if(cae==null||!cae.matches("[0-9]{14}")||vencimiento==null) throw conflicto("Respuesta fiscal incompleta");
  i.cae=cae;i.vencimiento=vencimiento;i.estado=AUTORIZADO;
  FacturaVenta f=i.factura;f.setEstado("EMITIDA");f.setCae(cae);f.setCaeFechaVencimiento(Date.from(vencimiento.atStartOfDay(ZoneId.of("America/Argentina/Buenos_Aires")).toInstant()));
  f.setNumeroFiscal(i.numero);f.setPuntoVentaFiscal(i.serie.getPuntoVenta());f.setTipoComprobanteFiscal(i.serie.getTipo());
  f.setAmbienteFiscal(i.serie.getAmbiente());f.setEmisorFiscal(i.serie.getEmisor());f.setResultadoAfip("A");f.setMotivoRechazo(null);auditar(f);liberar(i);
 }
 private void auditar(FacturaVenta f){f.setFechaModificacion(Date.from(reloj.instant()));f.setUsuarioModificacion(em.getReference(com.tp.facturacion.model.Usuario.class,usuarioSistemaId));}
 private boolean tieneCae(FacturaVenta f){return f.getCae()!=null&&!f.getCae().isBlank();}
 static boolean coincide(Snapshot a,Snapshot b){return b!=null&&a.concepto()==b.concepto()&&a.docTipo()==b.docTipo()&&a.docNumero()==b.docNumero()&&a.condicionIva()==b.condicionIva()&&a.fecha().equals(b.fecha())&&a.moneda().equals(b.moneda())&&a.cotizacion().compareTo(b.cotizacion())==0&&a.total().compareTo(b.total())==0;}
 private ReglaNegocioException conflicto(String m){return new ReglaNegocioException(m);}
 private String codificar(List<ErrorFiscal> errores){return errores.stream().map(e->e.codigo()+":"+Base64.getEncoder().encodeToString(sanear(e.mensaje()).getBytes(StandardCharsets.UTF_8))).collect(java.util.stream.Collectors.joining("\n"));}
 private String sanear(String m){if(m==null)return "";if(m.contains("<")||m.toLowerCase(Locale.ROOT).matches("(?s).*(token|sign|password|secret|credential|\\.pem).*"))return "Mensaje fiscal omitido";return m.replaceAll("[\\p{Cntrl}]"," ").substring(0,Math.min(500,m.length()));}
 private List<ErrorFiscal> decodificar(String s){if(s==null||s.isEmpty())return List.of();return s.lines().map(l->{String[] v=l.split(":",2);return new ErrorFiscal(Integer.parseInt(v[0]),new String(Base64.getDecoder().decode(v[1]),StandardCharsets.UTF_8));}).toList();}
}
