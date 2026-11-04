package com.tp.facturacion.arca;

import java.net.URI;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class ArcaPropertiesTests {
    static ArcaProperties configuradas(Path directorio) {
        var p = new ArcaProperties();
        p.setHabilitado(true);
        p.setCuit("20123456789");
        p.setCertificado(directorio.resolve("certificado.pem"));
        p.setClavePrivada(directorio.resolve("clave.pem"));
        p.setTicketCache(directorio.resolve("ticket.properties"));
        return p;
    }
    @Test void porDefectoNoPermiteUsarArca() {
        var p = new ArcaProperties();
        assertThrows(ArcaConfiguracionException.class, p::exigirHabilitada);
        p.setHabilitado(true);
        assertThrows(ArcaConfiguracionException.class, p::validarConfiguracion);
    }
    @Test void validaSoloEstructuraSinLeerRutas() {
        var p = configuradas(Path.of("/ruta-inexistente-de-prueba"));
        assertDoesNotThrow(p::validarConfiguracion);
        p.setClavePrivada(Path.of("relativa.key"));
        assertThrows(ArcaConfiguracionException.class, p::validarConfiguracion);
        p.setClavePrivada(Path.of("/clave.key"));
        p.setCuit("marcador-secreto");
        var ex = assertThrows(ArcaConfiguracionException.class, p::validarConfiguracion);
        assertFalse(ex.getMessage().contains("marcador-secreto"));
        assertFalse(p.toString().contains("marcador-secreto"));
    }
    @ParameterizedTest @ValueSource(strings={"https://wsaa.afip.gov.ar/ws/services/LoginCms", "https://usuario@wsaahomo.afip.gov.ar/ws/services/LoginCms", "https://wsaahomo.afip.gov.ar/ws/services/LoginCms?q=secreto", "https://wsaahomo.afip.gov.ar/ws/services/LoginCms#secreto"})
    void rechazaDestinosNoExactos(String uri) {
        var p = configuradas(Path.of("/prueba"));
        p.setWsaaUrl(URI.create(uri));
        assertThrows(ArcaConfiguracionException.class, p::validarConfiguracion);
    }
    @Test void rechazaWsfeProduccion() {
        var p = configuradas(Path.of("/prueba"));
        p.setWsfeUrl(URI.create("https://servicios1.afip.gov.ar/wsfev1/service.asmx"));
        assertThrows(ArcaConfiguracionException.class, p::validarConfiguracion);
    }
    @Test void erroresNoExponenEntradaNiCausas() {
        for (var error : new RuntimeException[]{new ArcaConfiguracionException("TOKEN-SECRETO"),new ArcaRemotaException("TOKEN-SECRETO")}) {
            assertFalse(error.toString().contains("TOKEN-SECRETO"));
            assertFalse(error.getMessage().contains("TOKEN-SECRETO"));
            assertNull(error.getCause());
        }
    }
}
