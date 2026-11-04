package com.tp.facturacion.arca;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicLong;

public class WsaaClient {
    public static final String NS="http://wsaa.view.sua.dvadac.desein.afip.gov";
    private static final AtomicLong ULTIMO_ID=new AtomicLong();
    private static final java.util.concurrent.ConcurrentHashMap<String,Instant> REINTENTAR_DESDE=new java.util.concurrent.ConcurrentHashMap<>();
    private final ArcaProperties propiedades;
    private final SoapHttpClient soap;
    private final FirmadorCms firmador;
    private final AlmacenTicket almacen;
    private final Clock reloj;
    public WsaaClient(ArcaProperties propiedades,SoapHttpClient soap,FirmadorCms firmador,AlmacenTicket almacen,Clock reloj) {
        this.propiedades=propiedades;this.soap=soap;this.firmador=firmador;this.almacen=almacen;this.reloj=reloj;
    }
    public TicketAcceso obtenerTicket() {
        propiedades.validarConfiguracion();
        var credenciales=CredencialesArca.cargar(propiedades,reloj);
        String identidad;
        try {
            String componentes=propiedades.getCuit()+"\nwsfe\n"+propiedades.getWsaaUrl()+"\n"+propiedades.getWsfeUrl()+"\n"+credenciales.huellaCertificado();
            identidad=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(componentes.getBytes(StandardCharsets.UTF_8)));
        } catch(Exception ex) {throw new ArcaConfiguracionException();}
        return almacen.obtener(identidad,reloj,()->autenticar(credenciales,identidad));
    }
    private TicketAcceso autenticar(CredencialesArca credenciales,String identidad) {
        Instant permitido=REINTENTAR_DESDE.get(identidad);
        if(permitido!=null && reloj.instant().isBefore(permitido)) throw new ArcaRemotaException();
        try {
            TicketAcceso ticket=realizarAutenticacion(credenciales);
            REINTENTAR_DESDE.remove(identidad);
            return ticket;
        } catch(ArcaRemotaException ex) {
            REINTENTAR_DESDE.put(identidad,reloj.instant().plusSeconds(60));
            throw ex;
        }
    }
    private TicketAcceso realizarAutenticacion(CredencialesArca credenciales) {
        Instant ahora=reloj.instant();
        long id=ULTIMO_ID.updateAndGet(anterior->Math.max(anterior+1,ahora.getEpochSecond()));
        String tra="<loginTicketRequest version=\"1.0\"><header><uniqueId>"+id+"</uniqueId><generationTime>"+ahora.minusSeconds(600)+"</generationTime><expirationTime>"+ahora.plusSeconds(600)+"</expirationTime></header><service>wsfe</service></loginTicketRequest>";
        String cms=firmador.firmarBase64(tra,credenciales);
        var doc=soap.llamar(propiedades.getWsaaUrl(),"","<loginCms xmlns=\""+NS+"\"><in0>"+XmlSeguro.escapar(cms)+"</in0></loginCms>");
        var body=XmlSeguro.exigirElemento(doc.getDocumentElement(),SoapHttpClient.NS,"Body");
        var respuesta=XmlSeguro.exigirElemento(body,NS,"loginCmsResponse");
        String interior=XmlSeguro.texto(respuesta,NS,"loginCmsReturn");
        try {
            var ticket=XmlSeguro.parsear(interior).getDocumentElement();
            if(ticket.getNamespaceURI()!=null || !("loginTicketResponse".equals(ticket.getLocalName())||"LoginTicketResponse".equals(ticket.getLocalName()))) throw new ArcaRemotaException();
            var cabecera=XmlSeguro.exigirElemento(ticket,null,"header");
            var acceso=XmlSeguro.exigirElemento(ticket,null,"credentials");
            TicketAcceso resultado=new TicketAcceso(XmlSeguro.texto(acceso,null,"token"),XmlSeguro.texto(acceso,null,"sign"),Instant.parse(XmlSeguro.texto(cabecera,null,"expirationTime")));
            if(!resultado.vigente(reloj)) throw new ArcaRemotaException();
            return resultado;
        } catch(Exception ex) {throw new ArcaRemotaException();}
    }
}
