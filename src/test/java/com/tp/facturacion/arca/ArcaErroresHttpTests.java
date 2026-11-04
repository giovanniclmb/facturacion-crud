package com.tp.facturacion.arca;

import com.tp.facturacion.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ArcaErroresHttpTests {
    @RestController
    static class FallosPrueba {
        @GetMapping("/prueba/configuracion") void configuracion() {
            throw new ArcaConfiguracionException("CLAVE_NO_PUBLICABLE");
        }
        @GetMapping("/prueba/remoto") void remoto() {
            throw new ArcaRemotaException("TICKET_NO_PUBLICABLE");
        }
    }

    @Test void erroresLocalesResponden503SinSecretos() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new FallosPrueba())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        String json = mvc.perform(get("/prueba/configuracion"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.detalles").isArray()).andReturn().getResponse().getContentAsString();
        assertFalse(json.contains("CLAVE_NO_PUBLICABLE"));
    }

    @Test void erroresRemotosResponden502SinSecretos() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new FallosPrueba())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        String json = mvc.perform(get("/prueba/remoto"))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.mensaje").isString()).andReturn().getResponse().getContentAsString();
        assertFalse(json.contains("TICKET_NO_PUBLICABLE"));
    }
}
