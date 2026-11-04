package com.tp.facturacion.arca;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ArcaConfiguration {
    @Bean public Clock arcaClock() { return Clock.systemUTC(); }
    @Bean public HttpClient arcaHttpClient() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }
    @Bean public TransporteSoap arcaTransporteSoap(HttpClient cliente) {return new HttpTransporteSoap(cliente);}
    @Bean public SoapHttpClient arcaSoapHttpClient(TransporteSoap transporte) {return new SoapHttpClient(transporte);}
    @Bean public FirmadorCms arcaFirmadorCms() {return new FirmadorCms();}
    @Bean public AlmacenTicket arcaAlmacenTicket(ArcaProperties propiedades) {return new AlmacenTicket(propiedades);}
    @Bean public WsaaClient arcaWsaaClient(ArcaProperties propiedades,SoapHttpClient soap,FirmadorCms firmador,AlmacenTicket almacen,Clock reloj) {return new WsaaClient(propiedades,soap,firmador,almacen,reloj);}
    @Bean public WsfeClient arcaWsfeClient(ArcaProperties propiedades,SoapHttpClient soap) {return new WsfeClient(propiedades,soap);}
}
