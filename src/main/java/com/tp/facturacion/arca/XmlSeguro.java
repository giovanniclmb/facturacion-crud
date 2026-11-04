package com.tp.facturacion.arca;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

public final class XmlSeguro {
    private XmlSeguro() {}
    public static Document parsear(String xml) {
        try {
            if(xml==null || xml.length()>HttpTransporteSoap.MAX_BYTES) throw new ArcaRemotaException();
            var factory=DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true); factory.setXIncludeAware(false); factory.setExpandEntityReferences(false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities",false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,""); factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
            var builder=factory.newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler());
            builder.setEntityResolver((publicId,systemId)->{throw new org.xml.sax.SAXException("Resolución externa prohibida");});
            var doc=builder.parse(new InputSource(new StringReader(xml)));
            if(doc.getElementsByTagNameNS("http://www.w3.org/2001/XInclude","*").getLength()!=0) throw new ArcaRemotaException();
            return doc;
        } catch(Exception ex) { throw new ArcaRemotaException(); }
    }
    public static String escapar(String valor) {
        if(valor==null) throw new ArcaRemotaException();
        return valor.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;");
    }
    public static List<Element> hijos(Element padre) {
        var lista=new ArrayList<Element>();
        for(var n=padre.getFirstChild();n!=null;n=n.getNextSibling()) if(n instanceof Element e) lista.add(e);
        return lista;
    }
    public static List<Element> elementos(Element padre,String namespace,String nombre) {
        return hijos(padre).stream().filter(e->Objects.equals(namespace,e.getNamespaceURI())&&nombre.equals(e.getLocalName())).toList();
    }
    public static Element exigirElemento(Element padre,String namespace,String nombre) {
        var lista=elementos(padre,namespace,nombre);
        if(lista.size()!=1) throw new ArcaRemotaException();
        return lista.get(0);
    }
    public static Element opcional(Element padre,String namespace,String nombre) {
        var lista=elementos(padre,namespace,nombre);
        if(lista.size()>1) throw new ArcaRemotaException();
        return lista.isEmpty()?null:lista.get(0);
    }
    public static String texto(Element padre,String namespace,String nombre) {
        var e=exigirElemento(padre,namespace,nombre);
        if(!hijos(e).isEmpty() || e.getTextContent().isBlank()) throw new ArcaRemotaException();
        return e.getTextContent().trim();
    }
}
