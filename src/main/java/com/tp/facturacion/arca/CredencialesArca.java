package com.tp.facturacion.arca;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.util.Date;
import java.util.HexFormat;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

public final class CredencialesArca {
    private final X509Certificate certificado;
    private final PrivateKey clave;
    private final String huella;
    private CredencialesArca(X509Certificate certificado,PrivateKey clave,String huella) {
        this.certificado=certificado;this.clave=clave;this.huella=huella;
    }
    public static CredencialesArca cargar(ArcaProperties propiedades,Clock reloj) {
        propiedades.validarConfiguracion();
        try {
            Object pemCert=leerPem(propiedades.getCertificado());
            if(!(pemCert instanceof X509CertificateHolder holder)) throw new ArcaConfiguracionException();
            X509Certificate cert=new JcaX509CertificateConverter().getCertificate(holder);
            cert.checkValidity(Date.from(reloj.instant()));
            Object pemClave=leerPem(propiedades.getClavePrivada());
            var converter=new JcaPEMKeyConverter();
            PrivateKey key;
            if(pemClave instanceof PEMKeyPair par) key=converter.getKeyPair(par).getPrivate();
            else if(pemClave instanceof PrivateKeyInfo info) key=converter.getPrivateKey(info);
            else throw new ArcaConfiguracionException();
            if(!"RSA".equals(key.getAlgorithm()) || !"RSA".equals(cert.getPublicKey().getAlgorithm())) throw new ArcaConfiguracionException();
            byte[] desafio="verificación local de credenciales ARCA".getBytes(StandardCharsets.UTF_8);
            var firma=Signature.getInstance("SHA256withRSA");firma.initSign(key);firma.update(desafio);byte[] prueba=firma.sign();
            firma.initVerify(cert.getPublicKey());firma.update(desafio);
            if(!firma.verify(prueba)) throw new ArcaConfiguracionException();
            String huella=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(cert.getEncoded()));
            return new CredencialesArca(cert,key,huella);
        } catch(Exception ex) {throw new ArcaConfiguracionException();}
    }
    private static Object leerPem(Path ruta) throws Exception {
        if(!Files.isRegularFile(ruta) || Files.size(ruta)>1024*1024) throw new ArcaConfiguracionException();
        try(var parser=new PEMParser(Files.newBufferedReader(ruta,StandardCharsets.UTF_8))) {
            Object objeto=parser.readObject();
            if(objeto==null || parser.readObject()!=null) throw new ArcaConfiguracionException();
            return objeto;
        }
    }
    public X509Certificate certificado() {return certificado;}
    public String huellaCertificado() {return huella;}
    PrivateKey clavePrivada() {return clave;}
    @Override public String toString() {return "CredencialesArca[material privado redactado]";}
}
