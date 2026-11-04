package com.tp.facturacion.arca;

import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

public class AlmacenTicket {
    private static final ConcurrentHashMap<Path,ReentrantLock> MUTEXES=new ConcurrentHashMap<>();
    private final ArcaProperties propiedades;
    private final Duration espera;
    private volatile Entrada memoria;
    private record Entrada(Path ruta,String identidad,TicketAcceso ticket,boolean persistida) {
        @Override public String toString(){return "Entrada[redactada]";}
    }
    public AlmacenTicket(ArcaProperties propiedades) {this(propiedades,Duration.ofSeconds(30));}
    AlmacenTicket(ArcaProperties propiedades,Duration espera) {this.propiedades=propiedades;this.espera=espera;}
    public TicketAcceso obtener(String identidad,Clock reloj,Supplier<TicketAcceso> renovar) {
        Path ruta=propiedades.getTicketCache();
        if(ruta==null || !ruta.isAbsolute() || identidad==null || identidad.isBlank()) throw new ArcaConfiguracionException();
        ruta=ruta.toAbsolutePath().normalize();
        Entrada guardado=memoria;
        if(guardado!=null && guardado.persistida() && ruta.equals(guardado.ruta()) && identidad.equals(guardado.identidad()) && guardado.ticket().vigente(reloj)) return guardado.ticket();
        ReentrantLock mutex=MUTEXES.computeIfAbsent(ruta,k->new ReentrantLock());
        boolean adquirido=false;
        long limite=System.nanoTime()+espera.toNanos();
        try {
            adquirido=mutex.tryLock(espera.toNanos(),TimeUnit.NANOSECONDS);
            if(!adquirido) throw new ArcaConfiguracionException();
            Path lock=Path.of(ruta+".lock");
            try(var canal=FileChannel.open(lock,Set.of(StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS),
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))) {
                Files.setPosixFilePermissions(lock,PosixFilePermissions.fromString("rw-------"));
                try(var bloqueo=bloquear(canal,limite)) {
                    Entrada disco=leer(ruta);
                    if(disco!=null && !identidad.equals(disco.identidad())) throw new ArcaConfiguracionException();
                    if(disco!=null && disco.ticket().vigente(reloj)) {
                        memoria=disco;return disco.ticket();
                    }
                    Entrada pendiente=memoria;
                    TicketAcceso ticket=pendiente!=null && !pendiente.persistida()
                            && ruta.equals(pendiente.ruta()) && identidad.equals(pendiente.identidad())
                            && pendiente.ticket().vigente(reloj) ? pendiente.ticket() : renovar.get();
                    if(ticket==null || !ticket.vigente(reloj)) throw new ArcaRemotaException();
                    // Una falla de disco sigue siendo 503; se retiene el TA para reintentar
                    // únicamente su persistencia en esta instancia, sin otro loginCms.
                    memoria=new Entrada(ruta,identidad,ticket,false);
                    guardar(ruta,identidad,ticket);
                    memoria=new Entrada(ruta,identidad,ticket,true);
                    return ticket;
                }
            }
        } catch(InterruptedException ex) {Thread.currentThread().interrupt();throw new ArcaConfiguracionException();}
        catch(ArcaRemotaException ex) {throw ex;}
        catch(Exception ex) {throw new ArcaConfiguracionException();}
        finally {if(adquirido) mutex.unlock();}
    }
    private FileLock bloquear(FileChannel canal,long limite) throws Exception {
        while(System.nanoTime()<limite) {
            try {FileLock lock=canal.tryLock();if(lock!=null) return lock;}
            catch(OverlappingFileLockException ocupado) { /* Otra ruta equivalente en esta JVM. */ }
            TimeUnit.MILLISECONDS.sleep(20);
        }
        throw new ArcaConfiguracionException();
    }
    private Entrada leer(Path ruta) {
        if(!Files.exists(ruta,LinkOption.NOFOLLOW_LINKS)) return null;
        try {
            if(!Files.isRegularFile(ruta,LinkOption.NOFOLLOW_LINKS) || Files.size(ruta)>HttpTransporteSoap.MAX_BYTES) throw new ArcaConfiguracionException();
            var permisos=Files.getPosixFilePermissions(ruta);
            if(!PosixFilePermissions.fromString("rw-------").containsAll(permisos)) throw new ArcaConfiguracionException();
            var datos=new Properties();
            try(var reader=Files.newBufferedReader(ruta,StandardCharsets.UTF_8)){datos.load(reader);}
            String identidad=datos.getProperty("identidad");
            if(identidad==null || identidad.isBlank()) throw new ArcaConfiguracionException();
            var ticket=new TicketAcceso(datos.getProperty("token"),datos.getProperty("sign"),Instant.parse(datos.getProperty("expiracion")));
            return new Entrada(ruta,identidad,ticket,true);
        } catch(Exception ex) {throw new ArcaConfiguracionException();}
    }
    private void guardar(Path ruta,String identidad,TicketAcceso ticket) {
        Path temporal=null;
        try {
            temporal=Files.createTempFile(ruta.getParent(),".arca-ticket-",".tmp",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            var datos=new Properties();datos.setProperty("identidad",identidad);datos.setProperty("token",ticket.token());datos.setProperty("sign",ticket.sign());datos.setProperty("expiracion",ticket.expiracion().toString());
            try(var writer=Files.newBufferedWriter(temporal,StandardCharsets.UTF_8)){datos.store(writer,"Ticket ARCA privado");}
            Files.move(temporal,ruta,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } catch(Exception ex) {throw new ArcaConfiguracionException();}
        finally {if(temporal!=null) try{Files.deleteIfExists(temporal);}catch(Exception ignorada){/* No exponer ruta ni credenciales. */}}
    }
}
