package com.tp.facturacion.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PaginacionDto {
    @Min(0)
    private int pagina = 0;

    @Min(1)
    @Max(100)
    private int tamanio = 20;
}
