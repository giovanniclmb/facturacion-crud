package com.tp.facturacion.arca;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class HttpTransporteSoap implements TransporteSoap {
    static final int MAX_BYTES = 2 * 1024 * 1024;
    private final HttpClient cliente;
    private final Duration timeout;
    public HttpTransporteSoap(HttpClient cliente) { this(cliente,Duration.ofSeconds(30)); }
    HttpTransporteSoap(HttpClient cliente, Duration timeout) {
        if (cliente.followRedirects() != HttpClient.Redirect.NEVER) throw new ArcaConfiguracionException();
        this.cliente=cliente; this.timeout=timeout;
    }
    @Override public RespuestaSoap enviar(URI destino, String accion, String sobre) {
        var lectura=Executors.newVirtualThreadPerTaskExecutor();
        java.util.concurrent.CompletableFuture<HttpResponse<InputStream>> pendiente=null;
        try {
            long limite=System.nanoTime()+timeout.toNanos();
            var solicitud=HttpRequest.newBuilder(destino).timeout(timeout)
                    .header("Content-Type","text/xml; charset=utf-8").header("SOAPAction","\""+accion+"\"")
                    .POST(HttpRequest.BodyPublishers.ofString(sobre,StandardCharsets.UTF_8)).build();
            pendiente=cliente.sendAsync(solicitud,HttpResponse.BodyHandlers.ofInputStream());
            var respuesta=pendiente.get(timeout.toNanos(),TimeUnit.NANOSECONDS);
            try (InputStream entrada=respuesta.body()) {
                if(respuesta.statusCode()<200 || respuesta.statusCode()>=300) throw new ArcaRemotaException();
                var cuerpo=lectura.submit(()->entrada.readNBytes(MAX_BYTES+1));
                try {
                    byte[] bytes=cuerpo.get(Math.max(1,limite-System.nanoTime()),TimeUnit.NANOSECONDS);
                    if(bytes.length>MAX_BYTES) throw new ArcaRemotaException();
                    return new RespuestaSoap(respuesta.statusCode(),bytes);
                } finally { cuerpo.cancel(true); }
            }
        } catch(InterruptedException ex) {
            Thread.currentThread().interrupt(); throw new ArcaRemotaException();
        } catch(Exception ex) { throw new ArcaRemotaException(); }
        finally { if(pendiente!=null) pendiente.cancel(true); lectura.shutdownNow(); }
    }
}
