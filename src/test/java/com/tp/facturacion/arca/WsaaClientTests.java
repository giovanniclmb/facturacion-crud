package com.tp.facturacion.arca;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import org.bouncycastle.cms.CMSSignedData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class WsaaClientTests {
    @TempDir Path dir;
    final Clock reloj=Clock.fixed(CredencialesPrueba.AHORA,ZoneOffset.UTC);
    static final String NS="http://wsaa.view.sua.dvadac.desein.afip.gov";
    String ticketXml(String raiz) {return "<"+raiz+"><header><expirationTime>2026-11-03T15:00:00Z</expirationTime></header><credentials><token>TOKEN-SECRETO</token><sign>SIGN-SECRETO</sign></credentials></"+raiz+">";}
    RespuestaSoap respuesta(String interior) {return new RespuestaSoap(200,SoapHttpClientTests.sobre("<loginCmsResponse xmlns='"+NS+"'><loginCmsReturn>"+XmlSeguro.escapar(interior)+"</loginCmsReturn></loginCmsResponse>").getBytes(StandardCharsets.UTF_8));}
    @Test void autenticaUnaVezFirmaTraCorrectoYReutilizaCache() throws Exception {
        var m=CredencialesPrueba.crear(dir,false);var n=new AtomicInteger();
        var soap=new SoapHttpClient((uri,accion,xml)-> {
            n.incrementAndGet();assertEquals(ArcaProperties.WSAA_HOMO,uri);assertEquals("",accion);
            try {
                var doc=XmlSeguro.parsear(xml);var body=XmlSeguro.exigirElemento(doc.getDocumentElement(),SoapHttpClient.NS,"Body");
                var op=XmlSeguro.exigirElemento(body,NS,"loginCms");
                String firmado=XmlSeguro.texto(op,NS,"in0");
                var cms=new CMSSignedData(Base64.getDecoder().decode(firmado));
                var tra=XmlSeguro.parsear(new String((byte[])cms.getSignedContent().getContent(),StandardCharsets.UTF_8));
                var root=tra.getDocumentElement();assertEquals("loginTicketRequest",root.getLocalName());
                assertEquals("wsfe",XmlSeguro.texto(root,null,"service"));
                var header=XmlSeguro.exigirElemento(root,null,"header");
                assertEquals(reloj.instant().minusSeconds(600),Instant.parse(XmlSeguro.texto(header,null,"generationTime")));
                assertEquals(reloj.instant().plusSeconds(600),Instant.parse(XmlSeguro.texto(header,null,"expirationTime")));
                assertTrue(Long.parseLong(XmlSeguro.texto(header,null,"uniqueId"))>0);
            } catch(Exception ex){throw new AssertionError(ex);}
            return respuesta(ticketXml("loginTicketResponse"));
        });
        var cliente=new WsaaClient(m.propiedades(),soap,new FirmadorCms(),new AlmacenTicket(m.propiedades()),reloj);
        assertEquals("TOKEN-SECRETO",cliente.obtenerTicket().token());
        assertEquals("SIGN-SECRETO",new WsaaClient(m.propiedades(),soap,new FirmadorCms(),new AlmacenTicket(m.propiedades()),reloj).obtenerTicket().sign());
        assertEquals(1,n.get());
    }
    @Test void admiteOtraRaizOficialYRechazaIncompletoSinRetry() throws Exception {
        int caso=0;
        for(String interno:new String[]{ticketXml("LoginTicketResponse"),"<loginTicketResponse><header><expirationTime>mal</expirationTime></header><credentials><token>x</token></credentials></loginTicketResponse>","<otra/>"}) {
            var m=CredencialesPrueba.crear(dir.resolve("caso-"+(caso++)),false);
            var n=new AtomicInteger();
            var cliente=new WsaaClient(m.propiedades(),new SoapHttpClient((u,a,x)->{n.incrementAndGet();return respuesta(interno);}),new FirmadorCms(),new AlmacenTicket(m.propiedades()),reloj);
            if(interno.contains("<LoginTicketResponse>")) assertNotNull(cliente.obtenerTicket());
            else assertThrows(ArcaRemotaException.class,cliente::obtenerTicket);
            assertEquals(1,n.get());
        }
    }
    @Test void falloRemotoAplicaBackoffSinDormirYPermiteRecuperarTrasSesentaSegundos() throws Exception {
        var m=CredencialesPrueba.crear(dir,false);var n=new AtomicInteger();
        var instante=new java.util.concurrent.atomic.AtomicReference<>(CredencialesPrueba.AHORA);
        Clock mutable=new Clock(){public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return this;}public Instant instant(){return instante.get();}};
        var soap=new SoapHttpClient((u,a,x)->{n.incrementAndGet();return new RespuestaSoap(200,SoapHttpClientTests.sobre("<s:Fault><faultstring>TOKEN-SECRETO</faultstring></s:Fault>").getBytes(StandardCharsets.UTF_8));});
        var cliente=new WsaaClient(m.propiedades(),soap,new FirmadorCms(),new AlmacenTicket(m.propiedades()),mutable);
        assertThrows(ArcaRemotaException.class,cliente::obtenerTicket);
        assertThrows(ArcaRemotaException.class,cliente::obtenerTicket);
        assertEquals(1,n.get());
        instante.set(CredencialesPrueba.AHORA.plusSeconds(60));
        assertThrows(ArcaRemotaException.class,cliente::obtenerTicket);
        assertEquals(2,n.get());
    }
    @Test void credencialInvalidaNoEnviaNada() throws Exception {
        var p=ArcaPropertiesTests.configuradas(dir);var n=new AtomicInteger();
        var cliente=new WsaaClient(p,new SoapHttpClient((u,a,x)->{n.incrementAndGet();return respuesta(ticketXml("loginTicketResponse"));}),new FirmadorCms(),new AlmacenTicket(p),reloj);
        assertThrows(ArcaConfiguracionException.class,cliente::obtenerTicket);assertEquals(0,n.get());
    }
}
