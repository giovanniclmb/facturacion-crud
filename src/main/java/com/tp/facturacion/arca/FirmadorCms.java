package com.tp.facturacion.arca;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;

public class FirmadorCms {
    public String firmarBase64(String tra,CredencialesArca credenciales) {
        try {
            var generador=new CMSSignedDataGenerator();
            var signer=new JcaContentSignerBuilder("SHA256withRSA").build(credenciales.clavePrivada());
            generador.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(new JcaDigestCalculatorProviderBuilder().build()).build(signer,credenciales.certificado()));
            generador.addCertificates(new JcaCertStore(List.of(credenciales.certificado())));
            var cms=generador.generate(new CMSProcessableByteArray(tra.getBytes(StandardCharsets.UTF_8)),true);
            return Base64.getEncoder().encodeToString(cms.getEncoded());
        } catch(Exception ex) {throw new ArcaConfiguracionException();}
    }
}
