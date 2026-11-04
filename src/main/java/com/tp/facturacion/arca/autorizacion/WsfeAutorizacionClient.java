package com.tp.facturacion.arca.autorizacion;

import com.tp.facturacion.arca.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import static com.tp.facturacion.arca.autorizacion.ContratoFiscal.*;

/** WSFE con un comprobante por solicitud; nunca conserva sobres ni credenciales. */
@Component
public class WsfeAutorizacionClient {
    private static final String NS=WsfeClient.NS;
    private static final DateTimeFormatter FECHA=DateTimeFormatter.BASIC_ISO_DATE;
    private final ArcaProperties propiedades;
    private final SoapHttpClient soap;
    public WsfeAutorizacionClient(ArcaProperties propiedades,SoapHttpClient soap) {this.propiedades=propiedades;this.soap=soap;}

    public ResultadoEnvio autorizar(TicketAcceso ticket,Envio envio) {
        validarSerie(envio.serie(),envio.numero());
        Snapshot s=envio.snapshot(); validarSnapshot(s);
        String identidad=campo("Concepto",s.concepto())+campo("DocTipo",s.docTipo())+campo("DocNro",s.docNumero())+campo("CbteDesde",envio.numero())+campo("CbteHasta",envio.numero())+campo("CbteFch",FECHA.format(s.fecha()));
        String detalle=identidad+campo("ImpTotal",s.total().toPlainString())+campo("ImpTotConc","0.00")+campo("ImpNeto",s.total().toPlainString())+campo("ImpOpEx","0.00")+campo("ImpTrib","0.00")+campo("ImpIVA","0.00")+campo("MonId","PES")+campo("MonCotiz","1")+campo("CondicionIVAReceptorId",s.condicionIva());
        String cabecera=campo("CantReg",1)+campo("PtoVta",envio.serie().puntoVenta())+campo("CbteTipo",11);
        Element resultado=llamar("FECAESolicitar",ticket,"<FeCAEReq><FeCabReq>"+cabecera+"</FeCabReq><FeDetReq><FECAEDetRequest>"+detalle+"</FECAEDetRequest></FeDetReq></FeCAEReq>");
        Element cab=elemento(resultado,"FeCabResp");
        exigir(numero(cab,"Cuit")==Long.parseLong(envio.serie().emisor()) && numero(cab,"PtoVta")==envio.serie().puntoVenta() && numero(cab,"CbteTipo")==11 && numero(cab,"CantReg")==1);
        var detalles=XmlSeguro.hijos(elemento(resultado,"FeDetResp"));exigir(detalles.size()==1);
        Element d=detalles.getFirst();nombre(d,"FECAEDetResponse");comprobarIdentidad(d,envio.numero(),s);
        String estado=texto(cab,"Resultado");exigir(estado.equals(texto(d,"Resultado")) && (estado.equals("A") || estado.equals("R")));
        List<ErrorFiscal> errores=mensajes(resultado,"Errors","Err",ticket),observaciones=mensajes(d,"Observaciones","Obs",ticket);
        if(estado.equals("A")) {
            exigir(errores.isEmpty());String cae=cae(d,"CAE");LocalDate vencimiento=fecha(d,"CAEFchVto");exigir(!vencimiento.isBefore(s.fecha()));
            return new ResultadoEnvio(true,cae,vencimiento,errores,observaciones);
        }
        exigir(vacio(d,"CAE") && vacio(d,"CAEFchVto"));
        return new ResultadoEnvio(false,null,null,errores,observaciones);
    }
    public Consulta consultar(TicketAcceso ticket,Serie serie,long numero) {
        validarSerie(serie,numero);
        Element resultado=llamar("FECompConsultar",ticket,"<FeCompConsReq>"+campo("CbteTipo",11)+campo("CbteNro",numero)+campo("PtoVta",serie.puntoVenta())+"</FeCompConsReq>");
        List<ErrorFiscal> errores=mensajes(resultado,"Errors","Err",ticket);
        Element d=XmlSeguro.opcional(resultado,NS,"ResultGet");
        if(!errores.isEmpty()) {
            exigir(errores.size()==1 && errores.getFirst().codigo()==602 && (d==null || XmlSeguro.hijos(d).isEmpty() && d.getTextContent().isBlank()));
            return new Consulta(Optional.empty());
        }
        exigir(d!=null);exigir(numero(d,"PtoVta")==serie.puntoVenta() && numero(d,"CbteTipo")==11 && numero(d,"CbteDesde")==numero && numero(d,"CbteHasta")==numero);
        exigir(texto(d,"Resultado").equals("A") && texto(d,"EmisionTipo").equals("CAE"));
        BigDecimal total=decimal(d,"ImpTotal");
        for(String cero:List.of("ImpTotConc","ImpOpEx","ImpTrib","ImpIVA")) exigir(decimal(d,cero).signum()==0);
        exigir(decimal(d,"ImpNeto").compareTo(total)==0);
        // Las colecciones fiscales no pueden introducir impuestos ausentes del snapshot.
        for(String array:List.of("Iva","Tributos","CbtesAsoc","Opcionales","Compradores","PeriodoAsoc","Actividades")) {
            Element extra=XmlSeguro.opcional(d,NS,array);exigir(extra==null || XmlSeguro.hijos(extra).isEmpty() && extra.getTextContent().isBlank());
        }
        Snapshot s=new Snapshot(entero(d,"Concepto"),entero(d,"DocTipo"),numero(d,"DocNro"),entero(d,"CondicionIVAReceptorId"),fecha(d,"CbteFch"),texto(d,"MonId"),decimal(d,"MonCotiz"),total);
        try {validarSnapshot(s);} catch(IllegalArgumentException ex) {throw new ArcaRemotaException();}
        mensajes(d,"Observaciones","Obs",ticket);
        String cae=cae(d,"CodAutorizacion");LocalDate vencimiento=fecha(d,"FchVto");exigir(!vencimiento.isBefore(s.fecha()));
        return new Consulta(Optional.of(new Comprobante(serie,numero,s,cae,vencimiento)));
    }
    private void validarSerie(Serie serie,long numero) {
        propiedades.validarConfiguracion();
        if(serie==null || !"HOMOLOGACION".equals(serie.ambiente()) || !propiedades.getCuit().equals(serie.emisor()) || serie.tipo()!=11 || serie.puntoVenta()<1 || serie.puntoVenta()>99999 || numero<1 || numero>99999999) throw new IllegalArgumentException("La serie fiscal no coincide con la configuración de homologación.");
    }
    private void validarSnapshot(Snapshot s) {
        if(s==null || s.concepto()!=1 || s.docTipo()!=80 || !SnapshotFiscalFactory.cuitValido(Long.toString(s.docNumero())) || Long.toString(s.docNumero()).equals(propiedades.getCuit()) || s.condicionIva()<1 || s.fecha()==null || !"PES".equals(s.moneda()) || s.cotizacion()==null || s.cotizacion().compareTo(BigDecimal.ONE)!=0 || s.total()==null || s.total().signum()<=0 || s.total().stripTrailingZeros().scale()>2 || s.total().precision()-s.total().scale()>16) throw new IllegalArgumentException("El snapshot fiscal no corresponde a Factura C de productos en PES.");
    }
    private Element llamar(String operacion,TicketAcceso ticket,String datos) {
        String auth="<Auth>"+campo("Token",ticket.token())+campo("Sign",ticket.sign())+campo("Cuit",propiedades.getCuit())+"</Auth>";
        var doc=soap.llamar(propiedades.getWsfeUrl(),NS+operacion,"<"+operacion+" xmlns=\""+NS+"\">"+auth+datos+"</"+operacion+">");
        var body=XmlSeguro.exigirElemento(doc.getDocumentElement(),SoapHttpClient.NS,"Body");
        return elemento(elemento(body,operacion+"Response"),operacion+"Result");
    }
    private void comprobarIdentidad(Element d,long numero,Snapshot s) {
        exigir(numero(d,"Concepto")==s.concepto() && numero(d,"DocTipo")==s.docTipo() && numero(d,"DocNro")==s.docNumero() && numero(d,"CbteDesde")==numero && numero(d,"CbteHasta")==numero && fecha(d,"CbteFch").equals(s.fecha()));
    }
    private List<ErrorFiscal> mensajes(Element padre,String contenedor,String item,TicketAcceso ticket) {
        Element lista=XmlSeguro.opcional(padre,NS,contenedor);if(lista==null) return List.of();
        List<ErrorFiscal> mensajes=new ArrayList<>();
        if(XmlSeguro.hijos(lista).isEmpty()) {exigir(lista.getTextContent().isBlank());return List.of();}
        for(Element e:XmlSeguro.hijos(lista)) {
            nombre(e,item);int codigo=entero(e,"Code");exigir(codigo>0);
            String mensaje=texto(e,"Msg");
            // No conservar XML ni autenticación aunque un servidor los refleje como mensaje.
            if(mensaje.contains("<") || mensaje.contains(">")) mensaje="Mensaje fiscal omitido por contener XML.";
            else mensaje=mensaje.replace(ticket.token(),"[redactado]").replace(ticket.sign(),"[redactado]").replaceAll("[\\p{Cntrl}]"," ");
            if(mensaje.length()>512) mensaje=mensaje.substring(0,512);
            mensajes.add(new ErrorFiscal(codigo,mensaje));
        }
        return List.copyOf(mensajes);
    }
    private static String campo(String nombre,Object valor) {return "<"+nombre+">"+XmlSeguro.escapar(valor.toString())+"</"+nombre+">";}
    private static Element elemento(Element e,String n) {return XmlSeguro.exigirElemento(e,NS,n);}
    private static String texto(Element e,String n) {return XmlSeguro.texto(e,NS,n);}
    private static long numero(Element e,String n) {try {String s=texto(e,n);exigir(s.matches("[0-9]+"));return Long.parseLong(s);}catch(Exception ex) {throw new ArcaRemotaException();}}
    private static int entero(Element e,String n) {long v=numero(e,n);exigir(v<=Integer.MAX_VALUE);return (int)v;}
    private static BigDecimal decimal(Element e,String n) {try {String s=texto(e,n);exigir(s.matches("[0-9]+(?:\\.[0-9]+)?"));return new BigDecimal(s);}catch(Exception ex) {throw new ArcaRemotaException();}}
    private static LocalDate fecha(Element e,String n) {try {String s=texto(e,n);exigir(s.matches("[0-9]{8}"));return LocalDate.parse(s,FECHA);}catch(Exception ex) {throw new ArcaRemotaException();}}
    private static String cae(Element e,String n) {String s=texto(e,n);exigir(s.matches("[0-9]{14}") && !s.equals("00000000000000"));return s;}
    private static boolean vacio(Element e,String n) {var v=XmlSeguro.opcional(e,NS,n);return v==null || XmlSeguro.hijos(v).isEmpty() && v.getTextContent().isBlank();}
    private static void nombre(Element e,String n) {exigir(NS.equals(e.getNamespaceURI()) && n.equals(e.getLocalName()));}
    private static void exigir(boolean condicion) {if(!condicion) throw new ArcaRemotaException();}
}
