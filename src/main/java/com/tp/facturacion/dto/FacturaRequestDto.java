package com.tp.facturacion.dto;

import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class FacturaRequestDto {

    /** Grupo de validación del alta (POST): ahí los datos de cabecera son obligatorios. */
    public interface Alta {}

    // Relaciones por ID (no se reciben entidades anidadas)
    @NotNull(groups = Alta.class)
    private Long puntoVentaId;

    @NotNull(groups = Alta.class)
    private Long clienteId;

    @NotNull(groups = Alta.class)
    private Long condicionIvaId;

    @NotNull(groups = Alta.class)
    private Long tipoMonedaId;

    // Opcionales
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "America/Argentina/Mendoza")
    private Date fechaEmision;   // si no viene, se usa la fecha actual

    private String observaciones;
    private String estado;       // solo se usa en el PUT

    @NotEmpty(groups = Alta.class)
    @Valid
    private List<FacturaDetalleRequestDto> detalles;
}