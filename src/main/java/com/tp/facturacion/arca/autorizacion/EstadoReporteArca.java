package com.tp.facturacion.arca.autorizacion;
import java.time.LocalDate;
/** Proyección escalar para evitar cargar entidades y relaciones por cada fila. */
public record EstadoReporteArca(Long facturaId, EstadoIntentoArca estado, Long numero,
        int puntoVenta, int tipo, LocalDate vencimiento) {}
