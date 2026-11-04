package com.tp.facturacion.dto;

public record ListaPrecioArticuloExplorerDto(Long id, Long articuloId, String articuloCodigo,
                                             String articuloDenominacion, String listaPrecioCodigo,
                                             String listaPrecioDenominacion, double precioVenta) {
}
