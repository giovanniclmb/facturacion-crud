package com.tp.facturacion.arca.autorizacion;

import com.tp.facturacion.arca.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.math.BigDecimal;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import static com.tp.facturacion.arca.autorizacion.ContratoFiscal.*;
import static org.junit.jupiter.api.Assertions.*;

class WsfeAutorizacionClientTests {
    static final String NS=WsfeClient.NS;
    final TicketAcceso ticket=new TicketAcceso("TOKEN<&", "SIGN>\"",Instant.parse("2026-11-03T15:00:00Z"));
    final Serie serie=new Serie("HOMOLOGACION",SnapshotFiscalTests.EMISOR,1,11);
    final Snapshot snapshot=new Snapshot(1,80,20222222223L,5,LocalDate.of(2026,11,3),"PES",BigDecimal.ONE,new BigDecimal("100.01"));
    final Envio envio=new Envio(UUID.randomUUID(),serie,1,snapshot);
    WsfeAutorizacionClient cliente(String op,String result) { return cliente(op,result,x->{}); }
    WsfeAutorizacionClient cliente(String op,String result,Consumer<String> verificar) {
        var p=new ArcaProperties();p.setHabilitado(true);p.setCuit(serie.emisor());p.setCertificado(Path.of("/ficticio/cert"));p.setClavePrivada(Path.of("/ficticio/key"));p.setTicketCache(Path.of("/ficticio/cache"));
        return new WsfeAutorizacionClient(p,new SoapHttpClient((u,a,x)-> {
            assertEquals(NS+op,a);validarRequestContraWsdl(x,op);verificar.accept(x);
            String xml="<s:Envelope xmlns:s='"+SoapHttpClient.NS+"'><s:Body><"+op+"Response xmlns='"+NS+"'><"+op+"Result>"+result+"</"+op+"Result></"+op+"Response></s:Body></s:Envelope>";
            return new RespuestaSoap(200,xml.getBytes(StandardCharsets.UTF_8));
        }));
    }
    String identidad() { return "<Concepto>1</Concepto><DocTipo>80</DocTipo><DocNro>20222222223</DocNro><CbteDesde>1</CbteDesde><CbteHasta>1</CbteHasta><CbteFch>20261103</CbteFch>"; }
    String obs() { return "<Observaciones><Obs><Code>100</Code><Msg>Aviso fiscal</Msg></Obs></Observaciones>"; }
    String respuesta(String estado) {
        return "<FeCabResp><Cuit>"+serie.emisor()+"</Cuit><PtoVta>1</PtoVta><CbteTipo>11</CbteTipo><CantReg>1</CantReg><Resultado>"+estado+"</Resultado></FeCabResp><FeDetResp><FECAEDetResponse>"+identidad()+"<Resultado>"+estado+"</Resultado>"+obs()+ (estado.equals("A")?"<CAE>12345678901234</CAE><CAEFchVto>20261113</CAEFchVto>":"<CAE/><CAEFchVto/>")+"</FECAEDetResponse></FeDetResp>";
    }
    String consulta() {
        return "<ResultGet>"+identidad()+"<ImpTotal>100.01</ImpTotal><ImpTotConc>0</ImpTotConc><ImpNeto>100.01</ImpNeto><ImpOpEx>0</ImpOpEx><ImpTrib>0</ImpTrib><ImpIVA>0</ImpIVA><MonId>PES</MonId><MonCotiz>1</MonCotiz><CondicionIVAReceptorId>5</CondicionIVAReceptorId><Resultado>A</Resultado><CodAutorizacion>12345678901234</CodAutorizacion><EmisionTipo>CAE</EmisionTipo><FchVto>20261113</FchVto><PtoVta>1</PtoVta><CbteTipo>11</CbteTipo></ResultGet>";
    }
    @Test void emiteUnSoloComprobanteConOrdenWsdlYAutenticacionEscapada() {
        var r=cliente("FECAESolicitar",respuesta("A"),xml->{
            var doc=XmlSeguro.parsear(xml); var body=XmlSeguro.exigirElemento(doc.getDocumentElement(),SoapHttpClient.NS,"Body"); var op=XmlSeguro.exigirElemento(body,NS,"FECAESolicitar");
            var auth=XmlSeguro.exigirElemento(op,NS,"Auth");assertEquals(ticket.token(),XmlSeguro.texto(auth,NS,"Token"));
            var req=XmlSeguro.exigirElemento(op,NS,"FeCAEReq");var cab=XmlSeguro.exigirElemento(req,NS,"FeCabReq");assertEquals("1",XmlSeguro.texto(cab,NS,"CantReg"));
            var detalles=XmlSeguro.exigirElemento(req,NS,"FeDetReq");assertEquals(1,XmlSeguro.hijos(detalles).size());var d=XmlSeguro.hijos(detalles).getFirst();
            assertEquals(List.of("Concepto","DocTipo","DocNro","CbteDesde","CbteHasta","CbteFch","ImpTotal","ImpTotConc","ImpNeto","ImpOpEx","ImpTrib","ImpIVA","MonId","MonCotiz","CondicionIVAReceptorId"),XmlSeguro.hijos(d).stream().map(e->e.getLocalName()).toList());
            assertEquals("100.01",XmlSeguro.texto(d,NS,"ImpTotal"));assertEquals("5",XmlSeguro.texto(d,NS,"CondicionIVAReceptorId"));
        }).autorizar(ticket,envio);
        assertTrue(r.aceptado());assertEquals("12345678901234",r.cae());assertEquals(1,r.observaciones().size());assertTrue(r.errores().isEmpty());
    }
    @Test void rechazoExplicitoConservaCodigosSinInventarlos() {
        var r=cliente("FECAESolicitar",respuesta("R")).autorizar(ticket,envio);assertFalse(r.aceptado());assertNull(r.cae());assertEquals(100,r.observaciones().getFirst().codigo());
    }
    @Test void noAdoptaRespuestasIncompletasContradictoriasODuplicadas() {
        for(String xml:List.of("",respuesta("A").replace("<CAE>12345678901234</CAE>","<CAE/>"),respuesta("A").replace("20261113","20260230"),respuesta("A").replace("<CantReg>1","<CantReg>2"),respuesta("A").replace("<CbteHasta>1","<CbteHasta>2"),respuesta("A").replace("<Resultado>A</Resultado></FeCabResp>","<Resultado>R</Resultado></FeCabResp>"),respuesta("A").replace("<Cuit>20111111112","<Cuit>20222222223"),respuesta("R").replace("<CAE/>","<CAE>12345678901234</CAE>"),respuesta("A")+"<Errors><Err><Code>600</Code><Msg>Fallo</Msg></Err></Errors>")) {
            assertThrows(ArcaRemotaException.class,()->cliente("FECAESolicitar",xml).autorizar(ticket,envio));
        }
    }
    @Test void consulta602SoloSiEsUnicoYNoHayComprobante() {
        String error="<Errors><Err><Code>602</Code><Msg>Sin resultados</Msg></Err></Errors>";
        assertTrue(cliente("FECompConsultar",error).consultar(ticket,serie,1).comprobante().isEmpty());
        for(String xml:List.of(error+consulta(),error.replace("</Errors>","<Err><Code>600</Code><Msg>Otro</Msg></Err></Errors>"),"<Errors><Err><Code>600</Code><Msg>TOKEN-SECRETO</Msg></Err></Errors>","")) {
            var e=assertThrows(ArcaRemotaException.class,()->cliente("FECompConsultar",xml).consultar(ticket,serie,1));assertFalse(e.toString().contains("TOKEN-SECRETO"));
        }
    }
    @Test void consultaExigeTodosLosCamposYLosImportesCero() {
        var c=cliente("FECompConsultar",consulta()).consultar(ticket,serie,1).comprobante().orElseThrow();assertEquals(snapshot,c.snapshot());assertEquals(serie,c.serie());assertEquals(1,c.numero());
        for(String xml:List.of(consulta().replace("<ImpIVA>0</ImpIVA>",""),consulta().replace("<ImpTrib>0","<ImpTrib>1"),consulta().replace("<ImpNeto>100.01","<ImpNeto>100.00"),consulta().replace("<MonId>PES","<MonId>DOL"),consulta().replace("<CondicionIVAReceptorId>5</CondicionIVAReceptorId>",""),consulta().replace("<Resultado>A","<Resultado>R"),consulta().replace("<PtoVta>1","<PtoVta>2"))) assertThrows(ArcaRemotaException.class,()->cliente("FECompConsultar",xml).consultar(ticket,serie,1));
    }
    @Test void falloSoapNoEsNoEncontrado() {
        var p=new ArcaProperties();p.setHabilitado(true);p.setCuit(serie.emisor());p.setCertificado(Path.of("/f"));p.setClavePrivada(Path.of("/k"));p.setTicketCache(Path.of("/t"));
        var c=new WsfeAutorizacionClient(p,new SoapHttpClient((u,a,x)->{throw new IllegalStateException("TOKEN-SECRETO");}));
        assertThrows(ArcaRemotaException.class,()->c.consultar(ticket,serie,1));
    }
    void validarRequestContraWsdl(String xml,String op) {
        assertDoesNotThrow(()-> {
            String wsdl=new String(getClass().getResourceAsStream("/arca/autorizacion/wsfe-homologacion.wsdl").readAllBytes(),StandardCharsets.UTF_8);
            var contrato=XmlSeguro.parsear(wsdl);
            var schema=contrato.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema","schema").item(0);
            var factory=javax.xml.validation.SchemaFactory.newInstance(javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI);
            factory.setProperty(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD,"");factory.setProperty(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
            var validator=factory.newSchema(new javax.xml.transform.dom.DOMSource(schema)).newValidator();
            var request=XmlSeguro.parsear(xml);var body=XmlSeguro.exigirElemento(request.getDocumentElement(),SoapHttpClient.NS,"Body");
            validator.validate(new javax.xml.transform.dom.DOMSource(XmlSeguro.exigirElemento(body,NS,op)));
        });
    }
    @Test void rechazaMasDeUnDetalleYCaeNoNumerico() {
        String a=respuesta("A");String detalle=a.substring(a.indexOf("<FECAEDetResponse>"),a.indexOf("</FECAEDetResponse>")+"</FECAEDetResponse>".length());
        for(String xml:List.of(a.replace("</FeDetResp>",detalle+"</FeDetResp>"),a.replace("12345678901234","1234"),a.replace("12345678901234","ABCDEFGHIJKLMN")))
            assertThrows(ArcaRemotaException.class,()->cliente("FECAESolicitar",xml).autorizar(ticket,envio));
    }
    @Test void saneaMensajesConCredencialesYXmlSinCambiarCodigoFiscal() {
        var r=cliente("FECAESolicitar",respuesta("A").replace("Aviso fiscal","TOKEN&lt;&amp;")).autorizar(ticket,envio);
        assertEquals(100,r.observaciones().getFirst().codigo());assertFalse(r.toString().contains(ticket.token()));
    }
}
