package com.tp.facturacion.arca;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class HttpTransporteSoapTests {
    @Test void postNoSigueRedireccionYLimitaCuerpo() throws Exception {
        var servidor=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var destino=new AtomicInteger();
        servidor.createContext("/redirect",e->{ e.getResponseHeaders().set("Location","/destino");e.sendResponseHeaders(302,-1);e.close(); });
        servidor.createContext("/destino",e->{destino.incrementAndGet();e.sendResponseHeaders(200,-1);e.close();});
        servidor.createContext("/grande",e->{byte[] bytes=new byte[2*1024*1024+1];e.sendResponseHeaders(200,bytes.length);try(var out=e.getResponseBody()){out.write(bytes);} });
        servidor.createContext("/ok",e->{assertEquals("POST",e.getRequestMethod());assertEquals("\"accion\"",e.getRequestHeaders().getFirst("SOAPAction"));assertTrue(e.getRequestHeaders().getFirst("Content-Type").contains("utf-8"));byte[] b=e.getRequestBody().readAllBytes();e.sendResponseHeaders(200,b.length);try(var out=e.getResponseBody()){out.write(b);} });
        servidor.start();
        try {
            var t=new HttpTransporteSoap(new ArcaConfiguration().arcaHttpClient());
            URI base=URI.create("http://127.0.0.1:"+servidor.getAddress().getPort());
            assertThrows(ArcaRemotaException.class,()->t.enviar(base.resolve("/redirect"),"","secreto"));
            assertEquals(0,destino.get());
            assertThrows(ArcaRemotaException.class,()->t.enviar(base.resolve("/grande"),"","x"));
            assertEquals("á",new String(t.enviar(base.resolve("/ok"),"accion","á").cuerpo(),java.nio.charset.StandardCharsets.UTF_8));
        } finally {servidor.stop(0);}
    }
    @Test void timeoutIncluyeCuerpoLento() throws Exception {
        var servidor=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        servidor.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
        servidor.createContext("/lento",e->{e.sendResponseHeaders(200,100);try {Thread.sleep(1000);}catch(InterruptedException ex){Thread.currentThread().interrupt();}e.close();});
        servidor.start();
        try {
            var t=new HttpTransporteSoap(new ArcaConfiguration().arcaHttpClient(),Duration.ofMillis(100));
            assertThrows(ArcaRemotaException.class,()->t.enviar(URI.create("http://127.0.0.1:"+servidor.getAddress().getPort()+"/lento"),"","x"));
        } finally {servidor.stop(0);}
    }
}
