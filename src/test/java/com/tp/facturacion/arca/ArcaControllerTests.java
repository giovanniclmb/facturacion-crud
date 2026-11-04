package com.tp.facturacion.arca;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import com.tp.facturacion.controller.ArcaController;
import com.tp.facturacion.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ArcaControllerTests {
    ArcaProperties propiedades;
    WsaaClient wsaa;
    WsfeClient wsfe;
    MockMvc mvc;

    @BeforeEach void preparar() {
        propiedades = new ArcaProperties();
        propiedades.setHabilitado(true);
        propiedades.setCuit("00000000000");
        propiedades.setCertificado(Path.of("/solo-pruebas/certificado.crt"));
        propiedades.setClavePrivada(Path.of("/solo-pruebas/privada.key"));
        propiedades.setTicketCache(Path.of("/solo-pruebas/ticket.properties"));
        wsaa = mock(WsaaClient.class);
        wsfe = mock(WsfeClient.class);
        var ticket = new TicketAcceso("TOKEN_SINTETICO", "SIGN_SINTETICO", Instant.parse("2026-11-03T23:00:00Z"));
        when(wsfe.estadoServidores()).thenReturn(new EstadoServidores("OK", "OK", "OK"));
        when(wsaa.obtenerTicket()).thenReturn(ticket);
        when(wsfe.puntosVenta(ticket)).thenReturn(List.of(new PuntoVentaArca(1, "CAE", false, null)));
        when(wsfe.ultimoAutorizado(ticket, 1, 11)).thenReturn(42L);
        var servicio = new ArcaDiagnosticoService(propiedades, wsaa, wsfe);
        mvc = MockMvcBuilders.standaloneSetup(new ArcaController(servicio))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test void respuestaNoContieneCredenciales() throws Exception {
        String json = mvc.perform(get("/api/arca/diagnostico").param("puntoVenta", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ambiente").value("HOMOLOGACION"))
                .andExpect(jsonPath("$.tipoComprobante").value(11))
                .andExpect(jsonPath("$.servidores.baseDatos").value("OK"))
                .andExpect(jsonPath("$.ultimoNumeroAutorizado").value(42))
                .andReturn().getResponse().getContentAsString();
        assertFalse(json.contains("TOKEN_SINTETICO"));
        assertFalse(json.contains("SIGN_SINTETICO"));
        assertFalse(json.contains("00000000000"));
        assertFalse(json.contains("solo-pruebas"));
    }

    @ParameterizedTest @ValueSource(strings = {"0", "-1", "100000", "abc"})
    void parametroInvalidoResponde400(String valor) throws Exception {
        mvc.perform(get("/api/arca/diagnostico").param("puntoVenta", valor))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(wsaa, wsfe);
    }

    @Test void parametroAusenteResponde400() throws Exception {
        mvc.perform(get("/api/arca/diagnostico")).andExpect(status().isBadRequest());
        verifyNoInteractions(wsaa, wsfe);
    }

    @Test void deshabilitadaResponde503ConFormatoExistente() throws Exception {
        propiedades.setHabilitado(false);
        mvc.perform(get("/api/arca/diagnostico").param("puntoVenta", "1"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.mensaje").isString()).andExpect(jsonPath("$.detalles").isArray());
        verifyNoInteractions(wsaa, wsfe);
    }

    @Test void configuracionIncompletaResponde503() throws Exception {
        propiedades.setCertificado(null);
        mvc.perform(get("/api/arca/diagnostico").param("puntoVenta", "1"))
                .andExpect(status().isServiceUnavailable());
        verifyNoInteractions(wsaa, wsfe);
    }

    @Test void rechazoRemotoResponde502SinDetallesSensibles() throws Exception {
        when(wsaa.obtenerTicket()).thenThrow(new ArcaRemotaException("TOKEN_SINTETICO"));
        String json = mvc.perform(get("/api/arca/diagnostico").param("puntoVenta", "1"))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.status").value(502))
                .andReturn().getResponse().getContentAsString();
        assertFalse(json.contains("TOKEN_SINTETICO"));
    }

    @Test void puntoNoHabilitadoResponde400() throws Exception {
        when(wsfe.puntosVenta(any())).thenReturn(List.of());
        mvc.perform(get("/api/arca/diagnostico").param("puntoVenta", "1"))
                .andExpect(status().isBadRequest());
    }
    @Test void diagnosticoExponeAdvertenciaSiCatalogoDevuelve602() throws Exception {
        when(wsfe.puntosVenta(any())).thenThrow(new ArcaCatalogoSinResultadosException());
        mvc.perform(get("/api/arca/diagnostico").param("puntoVenta","1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.puntosVentaVerificados").value(false))
                .andExpect(jsonPath("$.advertencias[0]").isString())
                .andExpect(jsonPath("$.ultimoNumeroAutorizado").value(42));
    }
}
