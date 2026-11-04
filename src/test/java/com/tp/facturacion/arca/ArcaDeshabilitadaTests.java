package com.tp.facturacion.arca;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.junit.jupiter.api.Assertions.*;
class ArcaDeshabilitadaTests {
    @Test void beansArrancanSinArchivosNiRed() {
        new ApplicationContextRunner().withUserConfiguration(ArcaConfiguration.class)
                .withBean(ArcaProperties.class, ArcaProperties::new).run(c -> {
                    assertNull(c.getStartupFailure());
                    assertEquals(HttpClient.Redirect.NEVER,c.getBean(HttpClient.class).followRedirects());
                    assertEquals(Duration.ofSeconds(10),c.getBean(HttpClient.class).connectTimeout().orElseThrow());
                    assertEquals("Z",c.getBean(Clock.class).getZone().getId());
                });
    }
}
