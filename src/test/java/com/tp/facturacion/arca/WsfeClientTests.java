package com.tp.facturacion.arca;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
class WsfeClientTests {
    static final String NS="http://ar.gov.afip.dif.FEV1/";
    final TicketAcceso ticket=new TicketAcceso("TOKEN<&","SIGN>\"",CredencialesPrueba.AHORA.plusSeconds(3600));
    WsfeClient cliente(String operacion,String resultado) {
        return new WsfeClient(ArcaPropertiesTests.configuradas(Path.of("/solo-prueba")),new SoapHttpClient((u,a,x)->{
            assertEquals(ArcaProperties.WSFE_HOMO,u);assertEquals(NS+operacion,a);
            var doc=XmlSeguro.parsear(x);var body=XmlSeguro.exigirElemento(doc.getDocumentElement(),SoapHttpClient.NS,"Body");
            var op=XmlSeguro.exigirElemento(body,NS,operacion);
            if(operacion.equals("FEDummy")) assertNull(XmlSeguro.opcional(op,NS,"Auth"));
            else {
                var auth=XmlSeguro.exigirElemento(op,NS,"Auth");
                assertEquals(ticket.token(),XmlSeguro.texto(auth,NS,"Token"));
                assertEquals(ticket.sign(),XmlSeguro.texto(auth,NS,"Sign"));
                assertEquals("20123456789",XmlSeguro.texto(auth,NS,"Cuit"));
            }
            return new RespuestaSoap(200,SoapHttpClientTests.sobre("<"+operacion+"Response xmlns='"+NS+"'><"+operacion+"Result>"+resultado+"</"+operacion+"Result></"+operacion+"Response>").getBytes(StandardCharsets.UTF_8));
        }));
    }
    @Test void dummyYUltimoAutorizadoConCeroValido() {
        assertEquals(new EstadoServidores("OK","OK","OK"),cliente("FEDummy","<AppServer>OK</AppServer><DbServer>OK</DbServer><AuthServer>OK</AuthServer>").estadoServidores());
        assertEquals(0,cliente("FECompUltimoAutorizado","<PtoVta>1</PtoVta><CbteTipo>11</CbteTipo><CbteNro>0</CbteNro>").ultimoAutorizado(ticket,1,11));
    }
    @Test void puntosYCatalogoIvaCompletosYVacios() {
        var lista=cliente("FEParamGetPtosVenta","<ResultGet><PtoVenta><Nro>1</Nro><EmisionTipo>CAE</EmisionTipo><Bloqueado>N</Bloqueado><FchBaja/></PtoVenta><PtoVenta><Nro>2</Nro><EmisionTipo>CAEA</EmisionTipo><Bloqueado>S</Bloqueado><FchBaja>20261030</FchBaja></PtoVenta></ResultGet>").puntosVenta(ticket);
        assertEquals(new PuntoVentaArca(1,"CAE",false,null),lista.get(0));assertEquals(LocalDate.of(2026,10,30),lista.get(1).fechaBaja());assertTrue(lista.get(1).bloqueado());
        assertEquals(new CondicionIvaReceptor(5,"Consumidor Final","C"),cliente("FEParamGetCondicionIvaReceptor","<ResultGet><CondicionIvaReceptor><Id>5</Id><Desc>Consumidor Final</Desc><Cmp_Clase>C</Cmp_Clase></CondicionIvaReceptor></ResultGet>").condicionesIvaReceptor(ticket).get(0));
        assertTrue(cliente("FEParamGetPtosVenta","<ResultGet/>").puntosVenta(ticket).isEmpty());
        assertTrue(cliente("FEParamGetCondicionIvaReceptor","<ResultGet/>").condicionesIvaReceptor(ticket).isEmpty());
    }
    @ParameterizedTest @ValueSource(strings={"<AppServer>NO</AppServer><DbServer>OK</DbServer><AuthServer>OK</AuthServer>","<AppServer>OK</AppServer><DbServer>OK</DbServer>","<AppServer>OK</AppServer><AppServer>OK</AppServer><DbServer>OK</DbServer><AuthServer>OK</AuthServer>"})
    void noInformaExitoSiDummyNoEsOkOIncompleto(String xml){assertThrows(ArcaRemotaException.class,()->cliente("FEDummy",xml).estadoServidores());}
    @ParameterizedTest @ValueSource(strings={"<Errors><Err><Code>100</Code><Msg>TOKEN-SECRETO</Msg></Err></Errors><ResultGet/>","", "<ResultGet/><ResultGet/>", "<ResultGet><PtoVenta><Nro>1</Nro><EmisionTipo>CAE</EmisionTipo><Bloqueado>DESCONOCIDO</Bloqueado></PtoVenta></ResultGet>","<ResultGet><PtoVenta><Nro>noentero</Nro></PtoVenta></ResultGet>","<ResultGet><PtoVenta><Nro>1</Nro><EmisionTipo>CAE</EmisionTipo></PtoVenta></ResultGet>","<ResultGet><PtoVenta><Nro>1</Nro><EmisionTipo>CAE</EmisionTipo><Bloqueado>N</Bloqueado><FchBaja>00000000</FchBaja></PtoVenta></ResultGet>","<ResultGet><PtoVenta><Nro>1</Nro><EmisionTipo>CAE</EmisionTipo><Bloqueado>N</Bloqueado><FchBaja>20260230</FchBaja></PtoVenta></ResultGet>"})
    void puntosRechazanErrorHttp200DuplicadosYDatosIncoherentes(String xml){var e=assertThrows(ArcaRemotaException.class,()->cliente("FEParamGetPtosVenta",xml).puntosVenta(ticket));assertFalse(e.getMessage().contains("TOKEN-SECRETO"));}
    @ParameterizedTest @ValueSource(strings={"<PtoVta>1</PtoVta><CbteTipo>11</CbteTipo>","<PtoVta>1</PtoVta><CbteTipo>11</CbteTipo><CbteNro>-1</CbteNro>","<PtoVta>2</PtoVta><CbteTipo>11</CbteTipo><CbteNro>5</CbteNro>","<PtoVta>1</PtoVta><CbteTipo>11</CbteTipo><CbteNro>mal</CbteNro>","<PtoVta>1</PtoVta><CbteTipo>11</CbteTipo><CbteNro>5</CbteNro><Errors><Err/></Errors>"})
    void ultimoNuncaInventaCeroNiIgnoraErrores(String xml){assertThrows(ArcaRemotaException.class,()->cliente("FECompUltimoAutorizado",xml).ultimoAutorizado(ticket,1,11));}
    @Test void aceptaNilFalseOCeroYRechazaNilVerdaderoOInvalido() {
        for(String valor:new String[]{"false","0"}) assertEquals(1,cliente("FEParamGetPtosVenta","<ResultGet xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' xsi:nil='"+valor+"'><PtoVenta><Nro>1</Nro><EmisionTipo>CAE</EmisionTipo><Bloqueado>N</Bloqueado></PtoVenta></ResultGet>").puntosVenta(ticket).size());
        for(String valor:new String[]{"true","1","desconocido"}) assertThrows(ArcaRemotaException.class,()->cliente("FEParamGetPtosVenta","<ResultGet xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' xsi:nil='"+valor+"'/>").puntosVenta(ticket));
    }
    @Test void exigeOperacionYNamespaceNoSoloUnNombreGlobal() {
        var c=new WsfeClient(ArcaPropertiesTests.configuradas(Path.of("/prueba")),SoapHttpClientTests.cliente(SoapHttpClientTests.sobre("<x xmlns='otro'><FEDummyResult><AppServer>OK</AppServer></FEDummyResult></x>")));
        assertThrows(ArcaRemotaException.class,c::estadoServidores);
    }
    @Test void puntosDistinguenUnicoError602SinOcultarOtrosErrores() {
        String sinResultados="<Errors><Err><Code>602</Code><Msg>Sin Resultados</Msg></Err></Errors>";
        var error=assertThrows(ArcaRemotaException.class,()->cliente("FEParamGetPtosVenta",sinResultados).puntosVenta(ticket));
        assertEquals("ArcaCatalogoSinResultadosException",error.getClass().getSimpleName());
        for(String xml:new String[]{
                "<Errors><Err><Code>602</Code><Msg>Sin Resultados</Msg></Err><Err><Code>600</Code><Msg>Error</Msg></Err></Errors>",
                "<Errors><Err><Code>602</Code><Code>600</Code></Err></Errors>",
                "<Errors><Err><Code>600</Code></Err></Errors>"}) {
            var otro=assertThrows(ArcaRemotaException.class,()->cliente("FEParamGetPtosVenta",xml).puntosVenta(ticket));
            assertEquals(ArcaRemotaException.class,otro.getClass());
        }
        assertEquals(ArcaRemotaException.class,assertThrows(ArcaRemotaException.class,()->cliente("FEParamGetCondicionIvaReceptor",sinResultados).condicionesIvaReceptor(ticket)).getClass());
    }
}
