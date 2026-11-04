package com.tp.facturacion.arca;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ArcaDiagnosticoServiceTests {
    ArcaProperties propiedades;
    WsaaClient wsaa;
    WsfeClient wsfe;
    ArcaDiagnosticoService servicio;
    TicketAcceso ticket;

    @BeforeEach void preparar() {
        propiedades = new ArcaProperties();
        propiedades.setHabilitado(true);
        propiedades.setCuit("00000000000");
        propiedades.setCertificado(Path.of("/solo-pruebas/certificado.crt"));
        propiedades.setClavePrivada(Path.of("/solo-pruebas/privada.key"));
        propiedades.setTicketCache(Path.of("/solo-pruebas/ticket.properties"));
        wsaa = mock(WsaaClient.class);
        wsfe = mock(WsfeClient.class);
        servicio = new ArcaDiagnosticoService(propiedades, wsaa, wsfe);
        ticket = new TicketAcceso("TOKEN_SINTETICO", "SIGN_SINTETICO", Instant.parse("2026-11-03T23:00:00Z"));
        when(wsfe.estadoServidores()).thenReturn(new EstadoServidores("OK", "OK", "OK"));
        when(wsaa.obtenerTicket()).thenReturn(ticket);
        when(wsfe.puntosVenta(ticket)).thenReturn(List.of(new PuntoVentaArca(1, "CAE", false, null)));
        when(wsfe.ultimoAutorizado(ticket, 1, 11)).thenReturn(42L);
    }

    @Test void diagnosticoExponeSoloResultadoOperativoYFacturaC() {
        var resultado = servicio.diagnosticar(1);
        assertTrue(resultado.puntosVentaVerificados());
        assertTrue(resultado.advertencias().isEmpty());
        assertEquals("HOMOLOGACION", resultado.ambiente());
        assertEquals(1, resultado.puntoVenta());
        assertEquals(11, resultado.tipoComprobante());
        assertEquals(42L, resultado.ultimoNumeroAutorizado());
        assertEquals(ticket.expiracion(), resultado.ticketVencimiento());
        verify(wsfe).ultimoAutorizado(ticket, 1, 11);
        assertFalse(resultado.toString().contains("TOKEN_SINTETICO"));
        assertFalse(resultado.toString().contains("SIGN_SINTETICO"));
    }

    @Test void deshabilitadaNoLlamaServiciosExternos() {
        propiedades.setHabilitado(false);
        assertThrows(ArcaConfiguracionException.class, () -> servicio.diagnosticar(1));
        verifyNoInteractions(wsaa, wsfe);
    }

    @Test void configuracionIncompletaNoLlamaServiciosExternos() {
        propiedades.setCuit(null);
        assertThrows(ArcaConfiguracionException.class, () -> servicio.diagnosticar(1));
        verifyNoInteractions(wsaa, wsfe);
    }

    @ParameterizedTest @ValueSource(ints = {-1, 0, 100000})
    void validaRangoPuntoAntesDeLaRed(int numero) {
        assertThrows(IllegalArgumentException.class, () -> servicio.diagnosticar(numero));
        verifyNoInteractions(wsaa, wsfe);
    }

    @Test void servidorNoOperativoNoSolicitaTicketNiConsultasAutenticadas() {
        when(wsfe.estadoServidores()).thenReturn(new EstadoServidores("OK", "FAIL", "OK"));
        assertThrows(ArcaRemotaException.class, () -> servicio.diagnosticar(1));
        verifyNoInteractions(wsaa);
        verify(wsfe, never()).puntosVenta(any());
        verify(wsfe, never()).ultimoAutorizado(any(), anyInt(), anyInt());
    }

    @Test void puntoInexistenteNoConsultaNumero() {
        when(wsfe.puntosVenta(ticket)).thenReturn(List.of());
        assertThrows(IllegalArgumentException.class, () -> servicio.diagnosticar(1));
        verify(wsfe, never()).ultimoAutorizado(any(), anyInt(), anyInt());
    }

    @Test void puntoBloqueadoBajaODeCaeaNoConsultaNumero() {
        for (var punto : List.of(new PuntoVentaArca(1, "CAE", true, null),
                new PuntoVentaArca(1, "CAE", false, LocalDate.of(2026, 11, 1)),
                new PuntoVentaArca(1, "CAEA", false, null))) {
            when(wsfe.puntosVenta(ticket)).thenReturn(List.of(punto));
            assertThrows(IllegalArgumentException.class, () -> servicio.diagnosticar(1));
        }
        verify(wsfe, never()).ultimoAutorizado(any(), anyInt(), anyInt());
    }

    @Test void rechazosRemotosSePropaganSinDevolverExito() {
        when(wsaa.obtenerTicket()).thenThrow(new ArcaRemotaException("SECRETO_NO_PUBLICABLE"));
        var error = assertThrows(ArcaRemotaException.class, () -> servicio.diagnosticar(1));
        assertFalse(error.getMessage().contains("SECRETO_NO_PUBLICABLE"));
        verify(wsfe, never()).ultimoAutorizado(any(), anyInt(), anyInt());
    }
    @Test void catalogo602PermiteConsultaSinAfirmarHabilitacion() {
        when(wsfe.puntosVenta(ticket)).thenThrow(new ArcaCatalogoSinResultadosException());
        var resultado=assertDoesNotThrow(()->servicio.diagnosticar(1));
        assertFalse(resultado.puntosVentaVerificados());
        assertEquals(1,resultado.advertencias().size());
        assertEquals(42L,resultado.ultimoNumeroAutorizado());
        verify(wsfe).ultimoAutorizado(ticket,1,11);
    }

    @Test void otroErrorDePuntosNoSeOmite() {
        when(wsfe.puntosVenta(ticket)).thenThrow(new ArcaRemotaException());
        assertThrows(ArcaRemotaException.class,()->servicio.diagnosticar(1));
        verify(wsfe,never()).ultimoAutorizado(any(),anyInt(),anyInt());
    }

    @Test void catalogo602NoOcultaFalloDeUltimoAutorizado() {
        when(wsfe.puntosVenta(ticket)).thenThrow(new ArcaCatalogoSinResultadosException());
        when(wsfe.ultimoAutorizado(ticket,1,11)).thenThrow(new ArcaRemotaException());
        assertThrows(ArcaRemotaException.class,()->servicio.diagnosticar(1));
    }
}
