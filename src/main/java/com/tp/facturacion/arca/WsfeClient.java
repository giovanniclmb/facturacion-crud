package com.tp.facturacion.arca;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.w3c.dom.Element;

public class WsfeClient {
    public static final String NS="http://ar.gov.afip.dif.FEV1/";
    private final ArcaProperties propiedades;
    private final SoapHttpClient soap;
    public WsfeClient(ArcaProperties propiedades,SoapHttpClient soap) {this.propiedades=propiedades;this.soap=soap;}
    public EstadoServidores estadoServidores() {
        var resultado=llamar("FEDummy","");
        String app=XmlSeguro.texto(resultado,NS,"AppServer"),db=XmlSeguro.texto(resultado,NS,"DbServer"),auth=XmlSeguro.texto(resultado,NS,"AuthServer");
        if(!"OK".equals(app)||!"OK".equals(db)||!"OK".equals(auth)) throw new ArcaRemotaException();
        return new EstadoServidores(app,db,auth);
    }
    public List<PuntoVentaArca> puntosVenta(TicketAcceso ticket) {
        var get=coleccion(llamar("FEParamGetPtosVenta",auth(ticket)));
        var puntos=new ArrayList<PuntoVentaArca>();var numeros=new HashSet<Integer>();
        for(var e:XmlSeguro.hijos(get)) {
            exigirNombre(e,"PtoVenta");
            int numero=entero(XmlSeguro.texto(e,NS,"Nro"));
            String emision=XmlSeguro.texto(e,NS,"EmisionTipo"),bloqueo=XmlSeguro.texto(e,NS,"Bloqueado");
            if(numero<1||numero>99999||!numeros.add(numero)||!("CAE".equals(emision)||"CAEA".equals(emision))||!("S".equals(bloqueo)||"N".equals(bloqueo))) throw new ArcaRemotaException();
            LocalDate baja=null;
            var fecha=XmlSeguro.opcional(e,NS,"FchBaja");
            if(fecha!=null) {
                if(!XmlSeguro.hijos(fecha).isEmpty()) throw new ArcaRemotaException();
                String texto=fecha.getTextContent().trim();
                if(!texto.isEmpty()) {
                    try {if(!texto.matches("[0-9]{8}")) throw new ArcaRemotaException();baja=LocalDate.parse(texto,DateTimeFormatter.BASIC_ISO_DATE);}
                    catch(Exception ex){throw new ArcaRemotaException();}
                }
            }
            puntos.add(new PuntoVentaArca(numero,emision,"S".equals(bloqueo),baja));
        }
        return List.copyOf(puntos);
    }
    public long ultimoAutorizado(TicketAcceso ticket,int puntoVenta,int tipoComprobante) {
        var resultado=llamar("FECompUltimoAutorizado",auth(ticket)+"<PtoVta>"+puntoVenta+"</PtoVta><CbteTipo>"+tipoComprobante+"</CbteTipo>");
        if(entero(XmlSeguro.texto(resultado,NS,"PtoVta"))!=puntoVenta||entero(XmlSeguro.texto(resultado,NS,"CbteTipo"))!=tipoComprobante) throw new ArcaRemotaException();
        try {long numero=Long.parseLong(XmlSeguro.texto(resultado,NS,"CbteNro"));if(numero<0) throw new ArcaRemotaException();return numero;}
        catch(Exception ex) {throw new ArcaRemotaException();}
    }
    public List<CondicionIvaReceptor> condicionesIvaReceptor(TicketAcceso ticket) {
        var get=coleccion(llamar("FEParamGetCondicionIvaReceptor",auth(ticket)));
        var condiciones=new ArrayList<CondicionIvaReceptor>();var ids=new HashSet<Integer>();
        for(var e:XmlSeguro.hijos(get)) {
            exigirNombre(e,"CondicionIvaReceptor");int id=entero(XmlSeguro.texto(e,NS,"Id"));
            if(id<1||!ids.add(id)) throw new ArcaRemotaException();
            condiciones.add(new CondicionIvaReceptor(id,XmlSeguro.texto(e,NS,"Desc"),XmlSeguro.texto(e,NS,"Cmp_Clase")));
        }
        return List.copyOf(condiciones);
    }
    private String auth(TicketAcceso ticket) {
        return "<Auth><Token>"+XmlSeguro.escapar(ticket.token())+"</Token><Sign>"+XmlSeguro.escapar(ticket.sign())+"</Sign><Cuit>"+XmlSeguro.escapar(propiedades.getCuit())+"</Cuit></Auth>";
    }
    private Element llamar(String operacion,String contenido) {
        propiedades.validarConfiguracion();
        var doc=soap.llamar(propiedades.getWsfeUrl(),NS+operacion,"<"+operacion+" xmlns=\""+NS+"\">"+contenido+"</"+operacion+">");
        var body=XmlSeguro.exigirElemento(doc.getDocumentElement(),SoapHttpClient.NS,"Body");
        var respuesta=XmlSeguro.exigirElemento(body,NS,operacion+"Response");
        var resultado=XmlSeguro.exigirElemento(respuesta,NS,operacion+"Result");
        var errores=XmlSeguro.opcional(resultado,NS,"Errors");
        if(errores!=null && (!XmlSeguro.hijos(errores).isEmpty()||!errores.getTextContent().isBlank())) {
            var lista=XmlSeguro.hijos(errores);
            if("FEParamGetPtosVenta".equals(operacion) && lista.size()==1) {
                var error=lista.get(0);
                exigirNombre(error,"Err");
                String codigo=XmlSeguro.texto(error,NS,"Code");
                XmlSeguro.texto(error,NS,"Msg");
                var datos=XmlSeguro.opcional(resultado,NS,"ResultGet");
                if("602".equals(codigo) && (datos==null || (XmlSeguro.hijos(datos).isEmpty() && datos.getTextContent().isBlank()))) {
                    throw new ArcaCatalogoSinResultadosException();
                }
            }
            throw new ArcaRemotaException();
        }
        return resultado;
    }
    private Element coleccion(Element resultado) {
        var get=XmlSeguro.exigirElemento(resultado,NS,"ResultGet");
        if(get.hasAttributeNS("http://www.w3.org/2001/XMLSchema-instance","nil")) {
            String nil=get.getAttributeNS("http://www.w3.org/2001/XMLSchema-instance","nil").trim();
            if(!"false".equals(nil)&&!"0".equals(nil)) throw new ArcaRemotaException();
        }
        return get;
    }
    private int entero(String texto) {try{return Integer.parseInt(texto);}catch(Exception ex){throw new ArcaRemotaException();}}
    private void exigirNombre(Element e,String nombre) {if(!NS.equals(e.getNamespaceURI())||!nombre.equals(e.getLocalName())) throw new ArcaRemotaException();}
}
