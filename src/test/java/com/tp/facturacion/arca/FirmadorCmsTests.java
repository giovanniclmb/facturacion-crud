package com.tp.facturacion.arca;
import java.nio.file.Path;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class FirmadorCmsTests {
    @TempDir Path dir;
    @Test void firmaVerificableIncluyeTraYCertificado() throws Exception {
        var material=CredencialesPrueba.crear(dir,false);
        var cred=CredencialesArca.cargar(material.propiedades(),Clock.fixed(CredencialesPrueba.AHORA,ZoneOffset.UTC));
        String tra="<loginTicketRequest>contenido UTF-8 á</loginTicketRequest>";
        var cms=new CMSSignedData(Base64.getDecoder().decode(new FirmadorCms().firmarBase64(tra,cred)));
        assertArrayEquals(tra.getBytes(StandardCharsets.UTF_8),(byte[])cms.getSignedContent().getContent());
        assertEquals(1,cms.getCertificates().getMatches(null).size());
        assertEquals(1,cms.getSignerInfos().size());
        for(var firma:cms.getSignerInfos().getSigners()) assertTrue(firma.verify(new JcaSimpleSignerInfoVerifierBuilder().build(material.certificado())));
    }
}
