package com.tp.facturacion.arca;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.channels.FileChannel;
import java.time.*;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class AlmacenTicketTests {
    @TempDir Path dir;
    final Clock reloj=Clock.fixed(CredencialesPrueba.AHORA,ZoneOffset.UTC);
    TicketAcceso ticket() {return new TicketAcceso("TOKEN-SECRETO","SIGN-SECRETO",reloj.instant().plusSeconds(3600));}
    ArcaProperties props() {return ArcaPropertiesTests.configuradas(dir);}
    @Test void persistePrivadoReutilizaTrasReinicioYNoMezclaIdentidad() throws Exception {
        var p=props();var n=new AtomicInteger();
        new AlmacenTicket(p).obtener("identidad-A",reloj,()->{n.incrementAndGet();return ticket();});
        var segundo=new AlmacenTicket(p).obtener("identidad-A",reloj,()->{n.incrementAndGet();return ticket();});
        assertEquals("TOKEN-SECRETO",segundo.token());assertEquals(1,n.get());
        assertEquals(PosixFilePermissions.fromString("rw-------"),Files.getPosixFilePermissions(p.getTicketCache()));
        String anterior=Files.readString(p.getTicketCache());
        assertThrows(ArcaConfiguracionException.class,()->new AlmacenTicket(p).obtener("identidad-B",reloj,()->{n.incrementAndGet();return ticket();}));
        assertEquals(1,n.get());
        assertEquals(anterior,Files.readString(p.getTicketCache()));
    }
    @Test void cacheCorruptoNoSeSobrescribeNiFiltra() throws Exception {
        var p=props();Files.writeString(p.getTicketCache(),"TOKEN-SECRETO");
        var e=assertThrows(ArcaConfiguracionException.class,()->new AlmacenTicket(p).obtener("A",reloj,()->{fail("No debe autenticar con cache corrupto");return ticket();}));
        assertFalse(e.toString().contains("TOKEN-SECRETO"));assertEquals("TOKEN-SECRETO",Files.readString(p.getTicketCache()));
    }
    @Test void instanciasConcurrentesRenuevanSoloUnaVez() throws Exception {
        var p=props();var a=new AlmacenTicket(p);var b=new AlmacenTicket(p);var n=new AtomicInteger();
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            var futuros=new ArrayList<Future<TicketAcceso>>();
            for(int i=0;i<12;i++) {final var almacen=i%2==0?a:b;futuros.add(executor.submit(()->almacen.obtener("A",reloj,()->{n.incrementAndGet();return ticket();})));}
            for(var f:futuros) assertEquals("TOKEN-SECRETO",f.get(5,TimeUnit.SECONDS).token());
        }
        assertEquals(1,n.get());
    }
    @Test void falloPersistenciaNoReintentaAutenticacion() throws Exception {
        var p=props();var n=new AtomicInteger();var almacen=new AlmacenTicket(p);
        assertThrows(ArcaConfiguracionException.class,()->almacen.obtener("A",reloj,()->{n.incrementAndGet();try{Files.createDirectory(p.getTicketCache());}catch(Exception e){throw new AssertionError(e);}return ticket();}));
        assertThrows(ArcaConfiguracionException.class,()->almacen.obtener("A",reloj,()->{n.incrementAndGet();return ticket();}));
        assertEquals(1,n.get());
        Files.delete(p.getTicketCache());
        assertEquals("TOKEN-SECRETO",almacen.obtener("A",reloj,()->{n.incrementAndGet();return ticket();}).token());
        assertEquals(1,n.get(),"Recuperar persistencia no debe pedir otro ticket");
        assertTrue(Files.isRegularFile(p.getTicketCache()));
    }
    @Test void bloqueoOcupadoEnOtroProcesoAgotaEsperaAcotada() throws Exception {
        var p=props();Path lock=Path.of(p.getTicketCache()+".lock");
        var proceso=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-cp",System.getProperty("java.class.path"),Bloqueador.class.getName(),lock.toString()).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try(var reader=proceso.inputReader()) {
            assertEquals("LISTO",reader.readLine());
            assertThrows(ArcaConfiguracionException.class,()->new AlmacenTicket(p,Duration.ofMillis(100)).obtener("A",reloj,this::ticket));
        } finally {proceso.getOutputStream().close();assertTrue(proceso.waitFor(5,TimeUnit.SECONDS));}
        assertNotNull(new AlmacenTicket(p).obtener("A",reloj,this::ticket));
    }
    public static class Bloqueador {
        public static void main(String[] args) throws Exception {
            try(var canal=FileChannel.open(Path.of(args[0]),StandardOpenOption.CREATE,StandardOpenOption.WRITE);var lock=canal.lock()) {
                System.out.println("LISTO");System.out.flush();System.in.read();
            }
        }
    }
}
