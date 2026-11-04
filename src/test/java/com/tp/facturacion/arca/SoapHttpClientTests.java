package com.tp.facturacion.arca;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
class SoapHttpClientTests {
    static String sobre(String cuerpo) { return "<s:Envelope xmlns:s='http://schemas.xmlsoap.org/soap/envelope/'><s:Body>"+cuerpo+"</s:Body></s:Envelope>"; }
    static SoapHttpClient cliente(String respuesta) { return new SoapHttpClient((uri,accion,xml)->new RespuestaSoap(200,respuesta.getBytes(StandardCharsets.UTF_8))); }
    @Test void construyeSobreUtf8() {
        var cliente=new SoapHttpClient((uri,accion,xml)-> {
            assertEquals("operacion",accion);
            var doc=XmlSeguro.parsear(xml);
            assertEquals("Envelope",doc.getDocumentElement().getLocalName());
            assertTrue(xml.contains("á"));
            return new RespuestaSoap(200,sobre("<ok xmlns='urn:x'/>").getBytes(StandardCharsets.UTF_8));
        });
        assertNotNull(cliente.llamar(ArcaProperties.WSAA_HOMO,"operacion","<x>á</x>"));
    }
    @ParameterizedTest @ValueSource(strings={"<raiz/>","<s:Envelope xmlns:s='http://schemas.xmlsoap.org/soap/envelope/'/>","<s:Envelope xmlns:s='http://schemas.xmlsoap.org/soap/envelope/'><s:Body/></s:Envelope>","<s:Envelope xmlns:s='http://schemas.xmlsoap.org/soap/envelope/'><s:Body><a/><b/></s:Body></s:Envelope>"})
    void exigeSobreYBodyUnico(String xml) { assertThrows(ArcaRemotaException.class,()->cliente(xml).llamar(ArcaProperties.WSAA_HOMO,"","<x/>")); }
    @Test void faultYHttpNuncaExponenSecreto() {
        var ex=assertThrows(ArcaRemotaException.class,()->cliente(sobre("<s:Fault><faultstring>TOKEN-SECRETO</faultstring></s:Fault>")).llamar(ArcaProperties.WSAA_HOMO,"","<x/>"));
        assertFalse(ex.getMessage().contains("TOKEN-SECRETO"));
        for(int status:new int[]{302,400,500}) assertThrows(ArcaRemotaException.class,()->new SoapHttpClient((u,a,x)->new RespuestaSoap(status,new byte[0])).llamar(ArcaProperties.WSAA_HOMO,"","<x/>"));
    }
}
