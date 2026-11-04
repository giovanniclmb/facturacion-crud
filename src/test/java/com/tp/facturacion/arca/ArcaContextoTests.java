package com.tp.facturacion.arca;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:arca-deshabilitada;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false",
        "arca.habilitado=false", "arca.certificado=/no-existe/certificado.crt",
        "arca.clave-privada=/no-existe/privada.key", "arca.ticket-cache=/no-existe/ticket.properties"
})
@Import(ArcaContextoTests.SinRed.class)
class ArcaContextoTests {
    @TestConfiguration
    static class SinRed {
        @Bean @Primary TransporteSoap transporteProhibido() {
            return (destino, accion, sobre) -> { throw new AssertionError("No debe haber red con ARCA deshabilitada"); };
        }
    }
    @Autowired WebApplicationContext contexto;

    @Test void contextoCompletoArrancaSinArchivosYDiagnosticoResponde503() throws Exception {
        MockMvcBuilders.webAppContextSetup(contexto).build()
                .perform(get("/api/arca/diagnostico").param("puntoVenta", "1"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.status").value(503));
    }
}
