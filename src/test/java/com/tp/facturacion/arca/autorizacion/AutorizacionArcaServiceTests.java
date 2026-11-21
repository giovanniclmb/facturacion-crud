package com.tp.facturacion.arca.autorizacion;

import com.tp.facturacion.arca.*;
import com.tp.facturacion.dto.FacturaArcaDto;
import java.nio.file.Path;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.tp.facturacion.arca.autorizacion.ContratoFiscal.*;
import static com.tp.facturacion.arca.autorizacion.EstadoIntentoArca.*;

class AutorizacionArcaServiceTests {
    ArcaProperties props; WsaaClient wsaa; WsfeClient lecturas;
    WsfeAutorizacionClient cliente; AlmacenIntentosArca almacen; AutorizacionArcaService servicio;
    Serie serie = new Serie("HOMOLOGACION","20123456789",1,11);
    UUID id=UUID.randomUUID(); Reserva reserva=new Reserva(id,UUID.randomUUID(),true);
    TicketAcceso ticket=new TicketAcceso("TOKEN_FICTICIO","SIGN_FICTICIO",Instant.now().plusSeconds(7200));
    Snapshot snapshot=new Snapshot(1,80,20222222223L,5,LocalDate.of(2026,11,3),"PES",BigDecimal.ONE,new BigDecimal("100.00"));
    Envio envio=new Envio(id,serie,1,snapshot);
    @BeforeEach void preparar() {
        props=new ArcaProperties();props.setHabilitado(true);props.setCuit(serie.emisor());props.setPuntosVentaExclusivos(Set.of(1));
        props.setCertificado(Path.of("/ficticio/cert"));props.setClavePrivada(Path.of("/ficticio/key"));props.setTicketCache(Path.of("/ficticio/cache"));
        wsaa=mock(WsaaClient.class);lecturas=mock(WsfeClient.class);cliente=mock(WsfeAutorizacionClient.class);almacen=mock(AlmacenIntentosArca.class);
        servicio=new AutorizacionArcaService(props,wsaa,lecturas,cliente,almacen);
        when(almacen.estado(1)).thenReturn(estado(null));
        when(wsaa.obtenerTicket()).thenReturn(ticket);
        when(lecturas.condicionesIvaReceptor(ticket)).thenReturn(List.of(new CondicionIvaReceptor(5,"Consumidor Final","C/49")));
        when(almacen.reservar(1,serie,Set.of(5),true)).thenReturn(reserva);
        when(lecturas.ultimoAutorizado(ticket,1,11)).thenReturn(0L);
        when(almacen.comenzarEnvio(reserva,1)).thenReturn(Optional.of(envio));
        when(cliente.autorizar(ticket,envio)).thenReturn(new ResultadoEnvio(true,"12345678901234",LocalDate.of(2026,11,13),List.of(),List.of()));
    }
    FacturaArcaDto estado(EstadoIntentoArca e) {
        boolean libre=e==null||e==RECHAZADO||e==FALLIDO_ANTES_ENVIO;
        return new FacturaArcaDto(1,37L,e==null?null:id,e,e==null?null:1L,1,11,"HOMOLOGACION",e==AUTORIZADO?"12345678901234":null,null,"Estado",List.of(),List.of(),libre,!libre&&e!=AUTORIZADO,libre,libre,e==null);
    }
    @Test void guardaEnvioAntesDeDespacharYSoloUnaVez() {
        when(almacen.estado(1)).thenReturn(estado(null),estado(AUTORIZADO));
        assertEquals(AUTORIZADO,servicio.autorizar(1,true).estadoFiscal());
        var orden=inOrder(almacen,cliente);
        orden.verify(almacen).comenzarEnvio(reserva,1);
        orden.verify(cliente).autorizar(ticket,envio);
        orden.verify(almacen).finalizar(eq(id),any());
        verify(cliente,times(1)).autorizar(any(),any());
    }
    @Test void aceptadaConFalloDeGuardadoSeConsultaSinReenviar() {
        doThrow(new IllegalStateException("fallo de disco")).when(almacen).finalizar(eq(id),any());
        when(almacen.estado(1)).thenReturn(estado(null),estado(INCIERTO),estado(INCIERTO),estado(AUTORIZADO));
        when(almacen.envioPendiente(1)).thenReturn(Optional.of(envio));
        when(cliente.consultar(ticket,serie,1)).thenReturn(new Consulta(Optional.of(new Comprobante(serie,1,snapshot,"12345678901234",LocalDate.of(2026,11,13)))));
        assertEquals(INCIERTO,servicio.autorizar(1,true).estadoFiscal());
        assertEquals(AUTORIZADO,servicio.consultar(1).estadoFiscal());
        verify(cliente,times(1)).autorizar(any(),any());
        verify(almacen).recuperar(eq(id),any());
    }
    @Test void estadoActivoOAutorizadoNoReenviaNiAutentica() {
        for(var e:List.of(PREPARANDO,ENVIANDO,INCIERTO,AUTORIZADO)) {
            when(almacen.estado(1)).thenReturn(estado(e));
            assertEquals(e,servicio.autorizar(1,true).estadoFiscal());
        }
        verifyNoInteractions(wsaa,lecturas,cliente);
    }
    @Test void faltaConfirmacionNoLeeRedNiReserva() {
        assertThrows(IllegalArgumentException.class,()->servicio.autorizar(1,false));
        verifyNoInteractions(wsaa,lecturas,cliente);
        verify(almacen,never()).reservar(anyLong(),any(),any(),anyBoolean());
    }
    @Test void ultimoFallaAntesDelEnvioYCierraSoloPreparacionPropia() {
        when(lecturas.ultimoAutorizado(any(),anyInt(),anyInt())).thenThrow(new ArcaRemotaException());
        assertThrows(ArcaRemotaException.class,()->servicio.autorizar(1,true));
        verify(almacen).fallarAntesEnvio(reserva);
        verifyNoInteractions(cliente);
    }
    @Test void dueNoAnteriorNoDespachaSiPerdioCas() {
        when(almacen.comenzarEnvio(reserva,1)).thenReturn(Optional.empty());
        servicio.autorizar(1,true);verifyNoInteractions(cliente);
    }
    @Test void consultaSinRegistroMantieneBloqueoYNoAutoriza() {
        when(almacen.estado(1)).thenReturn(estado(INCIERTO));
        when(almacen.envioPendiente(1)).thenReturn(Optional.of(envio));
        when(cliente.consultar(ticket,serie,1)).thenReturn(new Consulta(Optional.empty()));
        var resultado=servicio.consultar(1);
        assertEquals(INCIERTO,resultado.estadoFiscal());assertFalse(resultado.puedeModificar());
        verify(cliente,never()).autorizar(any(),any());verify(almacen,never()).recuperar(any(),any());
    }
    @Test void cambioDeEmisorNoConsultaConCredencialesDeOtraSerie() {
        when(almacen.estado(1)).thenReturn(estado(INCIERTO));
        when(almacen.envioPendiente(1)).thenReturn(Optional.of(envio));
        props.setCuit("20999999999");
        assertThrows(com.tp.facturacion.exception.ReglaNegocioException.class,()->servicio.consultar(1));
        verifyNoInteractions(wsaa,cliente);
    }
    @Test void timeoutTrasDespachoDejaPendiente() {
        when(cliente.autorizar(any(),any())).thenThrow(new ArcaRemotaException());
        when(almacen.estado(1)).thenReturn(estado(null),estado(INCIERTO));
        assertEquals(INCIERTO,servicio.autorizar(1,true).estadoFiscal());
        verify(almacen).marcarIncierto(id);verify(almacen,never()).fallarAntesEnvio(any());
    }
    @Test void consultaDeEstadoPersistidoNoUsaRed() {
        servicio.estado(1);verifyNoInteractions(wsaa,lecturas,cliente);
    }
    @Test void consultarPreparandoNoSolicitaCaeYFrenaEjecutorAnterior() {
        when(almacen.estado(1)).thenReturn(estado(PREPARANDO),estado(FALLIDO_ANTES_ENVIO));
        when(almacen.recuperarPreparando(1, serie.emisor())).thenReturn(reserva);
        assertEquals(FALLIDO_ANTES_ENVIO,servicio.consultar(1).estadoFiscal());
        verify(almacen).fallarAntesEnvio(reserva);
        verifyNoInteractions(wsaa,lecturas,cliente);
    }
    @Test void pideClaseBYCondicionDeReceptorAUnEmisorResponsableInscripto() {
        var serieB = new Serie("HOMOLOGACION", serie.emisor(), 1, 6);
        props.setPerfilEmisor(ArcaProperties.PerfilEmisor.RESPONSABLE_INSCRIPTO);
        var previaB = new FacturaArcaDto(1, 37L, null, null, null, 1, 6, null, null, null,
                "Estado", List.of(), List.of(), true, false, true, true, true);
        var autorizadaB = new FacturaArcaDto(1, 37L, id, AUTORIZADO, 1L, 1, 6, "HOMOLOGACION",
                "12345678901234", LocalDate.of(2026, 11, 13), "Autorizado", List.of(), List.of(), false,
                false, false, false, false);
        when(almacen.estado(1)).thenReturn(previaB, autorizadaB);
        when(lecturas.condicionesIvaReceptor(ticket, "B")).thenReturn(List.of(
                new CondicionIvaReceptor(4, "IVA Sujeto Exento", "B"),
                new CondicionIvaReceptor(5, "Consumidor Final", "B")));
        when(almacen.reservar(1, serieB, Set.of(4, 5), true, 7L)).thenReturn(reserva);
        var capturaB = new Snapshot(1, 99, 0, 5, snapshot.fecha(), "PES", BigDecimal.ONE,
                new BigDecimal("121.00"), new BigDecimal("100.00"), new BigDecimal("21.00"));
        var envioB = new Envio(id, serieB, 1, capturaB);
        var respuestaB = new ResultadoEnvio(true, "12345678901234", LocalDate.of(2026, 11, 13), List.of(), List.of());
        when(almacen.comenzarEnvio(reserva, 1)).thenReturn(Optional.of(envioB));
        when(cliente.autorizar(ticket, envioB)).thenReturn(respuestaB);
        assertEquals(autorizadaB, servicio.autorizar(1, true, 7L));
        verify(lecturas).ultimoAutorizado(ticket, 1, 6);
        verify(almacen).reservar(1, serieB, Set.of(4, 5), true, 7L);
        verify(almacen).finalizar(id, respuestaB);
    }
    @Test void porDefectoNoAutorizaFacturaB() {
        when(almacen.estado(1)).thenReturn(new FacturaArcaDto(1, 37L, null, null, null, 1, 6,
                null, null, null, "Estado", List.of(), List.of(), true, false, true, true, true));
        assertThrows(com.tp.facturacion.exception.ReglaNegocioException.class, () -> servicio.autorizar(1, true, 7L));
        verifyNoInteractions(wsaa, lecturas, cliente);
    }
    @Test void xNoReservaNiInvocaWsaa() {
        when(almacen.estado(1)).thenReturn(new FacturaArcaDto(1,37L,null,null,null,1,0,
                null,null,null,"Comprobante interno",List.of(),List.of(),false,false,true,true,true));
        assertThrows(com.tp.facturacion.exception.ReglaNegocioException.class, () -> servicio.autorizar(1,true,7L));
        verifyNoInteractions(wsaa,lecturas,cliente);
        verify(almacen,never()).reservar(anyLong(),any(),any(),anyBoolean(),any());
    }
    @Test void puntoSinDeclaracionExclusivaNoEnviaNiRecuperaCae() {
        props.setPuntosVentaExclusivos(Set.of());
        assertThrows(ArcaConfiguracionException.class,()->servicio.autorizar(1,true));
        when(almacen.estado(1)).thenReturn(estado(INCIERTO));
        assertThrows(ArcaConfiguracionException.class,()->servicio.consultar(1));
        verifyNoInteractions(wsaa,lecturas,cliente);
    }
}
