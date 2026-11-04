package com.tp.facturacion.arca.autorizacion;

import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import com.tp.facturacion.arca.*;
import com.tp.facturacion.dto.FacturaArcaDto;
import com.tp.facturacion.exception.ReglaNegocioException;
import static com.tp.facturacion.arca.autorizacion.ContratoFiscal.*;
import static com.tp.facturacion.arca.autorizacion.EstadoIntentoArca.*;

/** Las operaciones remotas ocurren fuera de las transacciones del almacén. */
@Service
public class AutorizacionArcaService {
    private final ArcaProperties props;
    private final WsaaClient wsaa;
    private final WsfeClient lecturas;
    private final WsfeAutorizacionClient cliente;
    private final AlmacenIntentosArca almacen;

    public AutorizacionArcaService(ArcaProperties props, WsaaClient wsaa, WsfeClient lecturas,
            WsfeAutorizacionClient cliente, AlmacenIntentosArca almacen) {
        this.props=props; this.wsaa=wsaa; this.lecturas=lecturas; this.cliente=cliente; this.almacen=almacen;
    }
    public FacturaArcaDto estado(long facturaId) { return almacen.estado(facturaId); }

    public FacturaArcaDto autorizar(long facturaId, boolean confirmarCuit) {
        FacturaArcaDto previo=almacen.estado(facturaId);
        // Un segundo clic no puede despachar un intento pendiente o autorizado.
        if (previo.cae()!=null && !previo.cae().isBlank() || activo(previo.estadoFiscal())) return previo;
        props.validarAutorizacion(previo.puntoVenta());
        if (!previo.puedeAutorizar()) throw new ReglaNegocioException("La factura no permite autorización");
        if (!confirmarCuit) throw new IllegalArgumentException("Debe confirmar que el documento del cliente es un CUIT");
        TicketAcceso ticket=wsaa.obtenerTicket();
        Set<Integer> iva=lecturas.condicionesIvaReceptor(ticket).stream()
                .filter(c -> c.claseComprobante()!=null && Arrays.stream(c.claseComprobante().split("/"))
                        .map(String::trim).anyMatch("C"::equals))
                .map(CondicionIvaReceptor::id).collect(Collectors.toSet());
        if (iva.isEmpty()) throw new ArcaRemotaException();
        Serie serie=new Serie("HOMOLOGACION",props.getCuit(),previo.puntoVenta(),11);
        Reserva reserva=almacen.reservar(facturaId,serie,iva,true);
        if (!reserva.creada()) return almacen.estado(facturaId);
        return prepararYEnviar(facturaId,previo,reserva,serie,ticket);
    }
    private FacturaArcaDto prepararYEnviar(long facturaId, FacturaArcaDto previo,
            Reserva reserva, Serie serie, TicketAcceso ticket) {
        Optional<Envio> preparado;
        try {
            long ultimo=lecturas.ultimoAutorizado(ticket,serie.puntoVenta(),serie.tipo());
            if (ultimo<0 || ultimo>=99999999L) throw new ReglaNegocioException("Numeración fiscal fuera de rango");
            preparado=almacen.comenzarEnvio(reserva,ultimo+1);
        } catch (RuntimeException error) {
            // El método solo cierra PREPARANDO del mismo dueño. Si hubo un commit
            // ENVIANDO incierto, jamás lo libera por una excepción del commit.
            try { almacen.fallarAntesEnvio(reserva); } catch (RuntimeException sinConfirmar) { /* Estado durable consultable. */ }
            throw error;
        }
        if (preparado.isEmpty()) return almacen.estado(facturaId);
        Envio envio=preparado.get();
        try {
            ResultadoEnvio resultado=cliente.autorizar(ticket,envio);
            almacen.finalizar(envio.intento(),resultado);
        } catch (RuntimeException resultadoIncierto) {
            return pendiente(facturaId,previo,envio);
        }
        return almacen.estado(facturaId);
    }
    public FacturaArcaDto consultar(long facturaId) {
        FacturaArcaDto previo=almacen.estado(facturaId);
        if (!previo.puedeConsultar()) return previo;
        props.validarAutorizacion(previo.puntoVenta());
        if (previo.estadoFiscal()==PREPARANDO) {
            // Consultar nunca solicita CAE. Invalida al ejecutor anterior antes de
            // cerrar una preparación que todavía no alcanzó el punto de despacho.
            Reserva reserva=almacen.recuperarPreparando(facturaId);
            almacen.fallarAntesEnvio(reserva);
            return almacen.estado(facturaId);
        }
        Optional<Envio> pendiente=almacen.envioPendiente(facturaId);
        if (pendiente.isEmpty()) return almacen.estado(facturaId);
        Envio envio=pendiente.get();
        if (!"HOMOLOGACION".equals(envio.serie().ambiente()) || !Objects.equals(props.getCuit(),envio.serie().emisor()))
            throw new ReglaNegocioException("El emisor configurado no corresponde al intento pendiente");
        try {
            Consulta consulta=cliente.consultar(wsaa.obtenerTicket(),envio.serie(),envio.numero());
            if (consulta.comprobante().isPresent()) almacen.recuperar(envio.intento(),consulta.comprobante().get());
            else almacen.marcarIncierto(envio.intento());
        } catch (RuntimeException resultadoIncierto) {
            return pendiente(facturaId,previo,envio);
        }
        return almacen.estado(facturaId);
    }
    private FacturaArcaDto pendiente(long facturaId, FacturaArcaDto previo, Envio envio) {
        try { almacen.marcarIncierto(envio.intento()); } catch (RuntimeException sinConfirmar) { }
        try { return almacen.estado(facturaId); }
        catch (RuntimeException sinLectura) {
            // Si se perdió también la base, conserva la identidad conocida y nunca
            // devuelve permisos de edición como si el despacho no hubiera ocurrido.
            return new FacturaArcaDto(facturaId,previo.numeroLocal(),envio.intento(),INCIERTO,
                    envio.numero(),envio.serie().puntoVenta(),envio.serie().tipo(),envio.serie().ambiente(),
                    null,null,"Resultado pendiente. Consulte el estado antes de continuar.",List.of(),List.of(),
                    false,true,false,false,false);
        }
    }
    private boolean activo(EstadoIntentoArca estado) {
        return estado==PREPARANDO || estado==ENVIANDO || estado==INCIERTO || estado==AUTORIZADO;
    }
}
