package com.tp.facturacion.arca;

import java.time.Instant;
import com.fasterxml.jackson.annotation.JsonFormat;

/** Resultado operativo: nunca contiene CUIT, token, sign ni rutas privadas. */
public record ArcaDiagnosticoDto(
        String ambiente,
        int puntoVenta,
        int tipoComprobante,
        EstadoServidores servidores,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant ticketVencimiento,
        long ultimoNumeroAutorizado,
        boolean puntosVentaVerificados,
        java.util.List<String> advertencias) {
}
