package com.tp.facturacion.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import com.tp.facturacion.arca.autorizacion.EstadoIntentoArca;
import com.tp.facturacion.arca.autorizacion.ContratoFiscal.ErrorFiscal;

/** Resultado y permisos persistidos; nunca incluye credenciales ni XML autenticado. */
public record FacturaArcaDto(long facturaId, Long numeroLocal, UUID intentoId,
        EstadoIntentoArca estadoFiscal, Long numeroFiscal, int puntoVenta, int tipoComprobante,
        String ambiente, String cae, LocalDate caeVencimiento, String mensaje,
        List<ErrorFiscal> errores, List<ErrorFiscal> observaciones,
        boolean puedeAutorizar, boolean puedeConsultar, boolean puedeModificar,
        boolean puedeAnular, boolean puedeEliminar) {
    public FacturaArcaDto {
        errores = errores == null ? List.of() : List.copyOf(errores);
        observaciones = observaciones == null ? List.of() : List.copyOf(observaciones);
    }
}
