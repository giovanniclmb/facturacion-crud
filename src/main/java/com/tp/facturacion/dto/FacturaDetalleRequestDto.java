package com.tp.facturacion.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.groups.Default;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class FacturaDetalleRequestDto {

    @NotNull(groups = {FacturaRequestDto.Alta.class, Default.class})
    private Long articuloId;

    @NotNull(groups = {FacturaRequestDto.Alta.class, Default.class})
    @Positive(groups = {FacturaRequestDto.Alta.class, Default.class})
    private Double cantidad;

    // Opcionales
    @PositiveOrZero(groups = {FacturaRequestDto.Alta.class, Default.class})
    @DecimalMax(value = "100", groups = {FacturaRequestDto.Alta.class, Default.class})
    private Double porcentajeBonificacion;

    private String descripcion; // si no viene, se usa la denominación del artículo
}