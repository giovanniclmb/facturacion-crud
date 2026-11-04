package com.tp.facturacion.arca;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Date;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.util.io.pem.PemObject;
final class CredencialesPrueba {
    static final Instant AHORA=Instant.parse("2026-11-03T12:00:00Z");
    record Material(ArcaProperties propiedades,X509Certificate certificado,PrivateKey clave) {}
    static Material crear(Path dir,boolean pkcs1) throws Exception { return crear(dir,pkcs1,AHORA.minusSeconds(3600),AHORA.plusSeconds(86400)); }
    static Material crear(Path dir,boolean pkcs1,Instant desde,Instant hasta) throws Exception {
        Files.createDirectories(dir);
        var generador=KeyPairGenerator.getInstance("RSA");generador.initialize(2048);var par=generador.generateKeyPair();
        var nombre=new X500Name("CN=credencial-efimera-test");
        var builder=new JcaX509v3CertificateBuilder(nombre,BigInteger.valueOf(System.nanoTime()).abs(),Date.from(desde),Date.from(hasta),nombre,par.getPublic());
        var cert=new JcaX509CertificateConverter().getCertificate(builder.build(new JcaContentSignerBuilder("SHA256withRSA").build(par.getPrivate())));
        var props=ArcaPropertiesTests.configuradas(dir);
        try(var out=new JcaPEMWriter(Files.newBufferedWriter(props.getCertificado(),StandardCharsets.UTF_8))) {out.writeObject(cert);}
        byte[] key=pkcs1?PrivateKeyInfo.getInstance(par.getPrivate().getEncoded()).parsePrivateKey().toASN1Primitive().getEncoded():par.getPrivate().getEncoded();
        try(var out=new JcaPEMWriter(Files.newBufferedWriter(props.getClavePrivada(),StandardCharsets.UTF_8))) {out.writeObject(new PemObject(pkcs1?"RSA PRIVATE KEY":"PRIVATE KEY",key));}
        return new Material(props,cert,par.getPrivate());
    }
}
