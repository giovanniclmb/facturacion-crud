package com.tp.facturacion.dto;

import java.util.Date;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.Positive;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FacturaFiltroDto {

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date fechaDesde;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date fechaHasta;

    private String estado;
    private Double montoMinimo;

    @Positive
    private Long numeroFactura;

    @Positive
    private Long clienteId;

    private Boolean sinInformarArca;
}
