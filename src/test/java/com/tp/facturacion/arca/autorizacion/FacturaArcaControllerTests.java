package com.tp.facturacion.arca.autorizacion;

import java.util.*;
import com.tp.facturacion.dto.FacturaArcaDto;
import com.tp.facturacion.controller.FacturaArcaController;
import com.tp.facturacion.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.tp.facturacion.arca.autorizacion.EstadoIntentoArca.*;

class FacturaArcaControllerTests {
    AutorizacionArcaService servicio; MockMvc mvc;
    FacturaArcaDto dto(EstadoIntentoArca e) {
        return new FacturaArcaDto(1,37L,UUID.randomUUID(),e,1L,1,11,"HOMOLOGACION",null,null,"Estado",List.of(),List.of(),false,true,false,false,false);
    }
    @BeforeEach void preparar() {
        servicio=mock(AutorizacionArcaService.class);
        mvc=MockMvcBuilders.standaloneSetup(new FacturaArcaController(servicio)).setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    @Test void intentoInciertoDevuelve202() throws Exception {
        when(servicio.consultar(1)).thenReturn(dto(INCIERTO));
        mvc.perform(post("/api/facturas/1/arca/consultar")).andExpect(status().isAccepted())
           .andExpect(jsonPath("$.puedeModificar").value(false));
    }
    @Test void consultarPreparandoEs202YEstadoGetNoRealizaEnvio() throws Exception {
        when(servicio.estado(1)).thenReturn(dto(PREPARANDO));
        mvc.perform(get("/api/facturas/1/arca")).andExpect(status().isOk());
        verify(servicio).estado(1);verifyNoMoreInteractions(servicio);
    }
    @Test void autorizarSoloRecibeConfirmacionNoDatosFiscales() throws Exception {
        when(servicio.autorizar(1,true)).thenReturn(dto(AUTORIZADO));
        mvc.perform(post("/api/facturas/1/arca/autorizar").contentType(MediaType.APPLICATION_JSON)
           .content("{\"confirmarCuitReceptor\":true,\"numeroFiscal\":99,\"importeTotal\":1}"))
           .andExpect(status().isOk()).andExpect(jsonPath("$.numeroFiscal").value(1));
        verify(servicio).autorizar(1,true);
    }
    @Test void rechazoFiscalEsResultado200() throws Exception {
        when(servicio.autorizar(1,true)).thenReturn(dto(RECHAZADO));
        mvc.perform(post("/api/facturas/1/arca/autorizar").contentType(MediaType.APPLICATION_JSON)
           .content("{\"confirmarCuitReceptor\":true}")).andExpect(status().isOk());
    }
    @Test void cuerpoAusenteNoAutoriza() throws Exception {
        mvc.perform(post("/api/facturas/1/arca/autorizar")).andExpect(status().isBadRequest());
        verifyNoInteractions(servicio);
    }
}
