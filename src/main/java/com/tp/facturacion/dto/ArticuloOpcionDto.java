package com.tp.facturacion.dto;

/** Artículo con su precio vigente, para mostrar el precio al cargar un ítem. */
public record ArticuloOpcionDto(Long id, String descripcion, double precio) {
}