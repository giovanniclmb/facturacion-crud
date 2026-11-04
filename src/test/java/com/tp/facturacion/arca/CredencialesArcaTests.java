package com.tp.facturacion.arca;
import java.nio.file.*;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class CredencialesArcaTests {
    @TempDir Path dir;
    final Clock reloj=Clock.fixed(CredencialesPrueba.AHORA,ZoneOffset.UTC);
    @Test void soportaPkcs1YPkcs8ConParVerificado() throws Exception {
        for(boolean pkcs1:new boolean[]{true,false}) {
            var m=CredencialesPrueba.crear(dir,pkcs1);
            var c=CredencialesArca.cargar(m.propiedades(),reloj);
            assertEquals(m.certificado(),c.certificado());
            assertEquals(64,c.huellaCertificado().length());
            assertFalse(c.toString().contains("PRIVATE KEY"));
        }
    }
    @Test void rechazaAusentePemInesperadoYCifradoSinFiltrarRuta() throws Exception {
        var m=CredencialesPrueba.crear(dir,false);
        for(String texto:new String[]{"TOKEN-SECRETO","-----BEGIN ENCRYPTED PRIVATE KEY-----\nAAAA\n-----END ENCRYPTED PRIVATE KEY-----"}) {
            Files.writeString(m.propiedades().getClavePrivada(),texto);
            var e=assertThrows(ArcaConfiguracionException.class,()->CredencialesArca.cargar(m.propiedades(),reloj));
            assertFalse(e.getMessage().contains(dir.toString()));assertFalse(e.toString().contains("TOKEN-SECRETO"));
        }
        Files.delete(m.propiedades().getClavePrivada());
        assertThrows(ArcaConfiguracionException.class,()->CredencialesArca.cargar(m.propiedades(),reloj));
    }
    @Test void rechazaCertificadosVencidosFuturosYDeOtraClave() throws Exception {
        var vencido=CredencialesPrueba.crear(dir,false,CredencialesPrueba.AHORA.minusSeconds(86400),CredencialesPrueba.AHORA.minusSeconds(1));
        assertThrows(ArcaConfiguracionException.class,()->CredencialesArca.cargar(vencido.propiedades(),reloj));
        var futuro=CredencialesPrueba.crear(dir,false,CredencialesPrueba.AHORA.plusSeconds(1),CredencialesPrueba.AHORA.plusSeconds(86400));
        assertThrows(ArcaConfiguracionException.class,()->CredencialesArca.cargar(futuro.propiedades(),reloj));
        var propio=CredencialesPrueba.crear(dir,false);var otro=CredencialesPrueba.crear(dir.resolve("otro"),false);
        Files.copy(otro.propiedades().getClavePrivada(),propio.propiedades().getClavePrivada(),StandardCopyOption.REPLACE_EXISTING);
        assertThrows(ArcaConfiguracionException.class,()->CredencialesArca.cargar(propio.propiedades(),reloj));
    }
}
