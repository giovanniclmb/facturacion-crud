package com.tp.facturacion.dto;

import java.util.Date;

import org.springframework.format.annotation.DateTimeFormat;

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
}
