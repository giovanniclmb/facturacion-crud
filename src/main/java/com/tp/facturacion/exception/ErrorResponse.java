package com.tp.facturacion.exception;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Getter;

@Getter
public class ErrorResponse {

    private final int status;
    private final String mensaje;
    private final List<String> detalles;
    private final LocalDateTime fecha = LocalDateTime.now();

    public ErrorResponse(int status, String mensaje) {
        this(status, mensaje, List.of());
    }

    public ErrorResponse(int status, String mensaje, List<String> detalles) {
        this.status = status;
        this.mensaje = mensaje;
        this.detalles = detalles;
    }
}