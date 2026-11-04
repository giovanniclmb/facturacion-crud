package com.tp.facturacion.arca;

import org.springframework.stereotype.Service;

/** Orquesta consultas de homologación; no reserva números ni modifica facturas. */
@Service
public class ArcaDiagnosticoService {
    private final ArcaProperties propiedades;
    private final WsaaClient wsaa;
    private final WsfeClient wsfe;

    public ArcaDiagnosticoService(ArcaProperties propiedades, WsaaClient wsaa, WsfeClient wsfe) {
        this.propiedades = propiedades;
        this.wsaa = wsaa;
        this.wsfe = wsfe;
    }

    public ArcaDiagnosticoDto diagnosticar(int puntoVenta) {
        if (puntoVenta < 1 || puntoVenta > 99999) {
            throw new IllegalArgumentException("El punto de venta debe estar entre 1 y 99999");
        }
        propiedades.validarConfiguracion();

        EstadoServidores servidores = wsfe.estadoServidores();
        if (servidores == null || !"OK".equals(servidores.aplicacion())
                || !"OK".equals(servidores.baseDatos()) || !"OK".equals(servidores.autenticacion())) {
            throw new ArcaRemotaException();
        }

        TicketAcceso ticket = wsaa.obtenerTicket();
        boolean puntosVerificados = true;
        try {
            boolean habilitado = wsfe.puntosVenta(ticket).stream().anyMatch(punto ->
                    punto.numero() == puntoVenta && "CAE".equals(punto.emisionTipo())
                            && !punto.bloqueado() && punto.fechaBaja() == null);
            if (!habilitado) {
                throw new IllegalArgumentException("El punto de venta no está habilitado para CAE en homologación");
            }
        } catch (ArcaCatalogoSinResultadosException sinResultados) {
            // Este diagnóstico solo admite homologación. La consulta del último número
            // comprueba conectividad, pero no acredita habilitación para emitir.
            puntosVerificados = false;
        }

        long ultimo = wsfe.ultimoAutorizado(ticket, puntoVenta, 11);
        return new ArcaDiagnosticoDto("HOMOLOGACION", puntoVenta, 11, servidores,
                ticket.expiracion(), ultimo, puntosVerificados,
                puntosVerificados ? java.util.List.of() : java.util.List.of(
                        "ARCA devolvió 602 (Sin Resultados) al consultar puntos de venta. "
                        + "La consulta del último comprobante fue exitosa; no se verificó la habilitación del punto para emitir."));
    }
}
