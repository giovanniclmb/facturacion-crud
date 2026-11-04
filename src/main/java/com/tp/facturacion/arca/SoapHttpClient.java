package com.tp.facturacion.arca;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.w3c.dom.Document;

public class SoapHttpClient {
    public static final String NS="http://schemas.xmlsoap.org/soap/envelope/";
    private final TransporteSoap transporte;
    public SoapHttpClient(TransporteSoap transporte) { this.transporte=transporte; }
    public Document llamar(URI destino,String accion,String cuerpoXml) {
        try {
            var respuesta=transporte.enviar(destino,accion,"<?xml version=\"1.0\" encoding=\"UTF-8\"?><s:Envelope xmlns:s=\""+NS+"\"><s:Body>"+cuerpoXml+"</s:Body></s:Envelope>");
            if(respuesta.status()<200 || respuesta.status()>=300 || respuesta.cuerpo()==null || respuesta.cuerpo().length>HttpTransporteSoap.MAX_BYTES) throw new ArcaRemotaException();
            String xml=StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(respuesta.cuerpo())).toString();
            var doc=XmlSeguro.parsear(xml);
            var raiz=doc.getDocumentElement();
            if(!NS.equals(raiz.getNamespaceURI()) || !"Envelope".equals(raiz.getLocalName())) throw new ArcaRemotaException();
            var body=XmlSeguro.exigirElemento(raiz,NS,"Body");
            var hijos=XmlSeguro.hijos(body);
            if(hijos.size()!=1 || (NS.equals(hijos.get(0).getNamespaceURI())&&"Fault".equals(hijos.get(0).getLocalName()))) throw new ArcaRemotaException();
            return doc;
        } catch(Exception ex) { throw new ArcaRemotaException(); }
    }
}
