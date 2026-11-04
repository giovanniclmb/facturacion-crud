package com.tp.facturacion.arca.autorizacion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** Valores fiscales sin entidades persistentes ni credenciales. */
public final class ContratoFiscal {
    private ContratoFiscal() {}
    public record Serie(String ambiente, String emisor, int puntoVenta, int tipo) {}
    public record Snapshot(int concepto, int docTipo, long docNumero, int condicionIva,
            LocalDate fecha, String moneda, BigDecimal cotizacion, BigDecimal total) {}
    public record ErrorFiscal(int codigo, String mensaje) {}
    public record ResultadoEnvio(boolean aceptado, String cae, LocalDate vencimiento,
            List<ErrorFiscal> errores, List<ErrorFiscal> observaciones) {
        public ResultadoEnvio { errores=List.copyOf(errores); observaciones=List.copyOf(observaciones); }
    }
    public record Comprobante(Serie serie, long numero, Snapshot snapshot, String cae, LocalDate vencimiento) {}
    public record Consulta(Optional<Comprobante> comprobante) {}
    public record Reserva(UUID intento, UUID propietario, boolean creada) {}
    public record Envio(UUID intento, Serie serie, long numero, Snapshot snapshot) {}
}
