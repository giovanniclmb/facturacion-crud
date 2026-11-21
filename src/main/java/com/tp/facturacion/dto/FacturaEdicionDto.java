package com.tp.facturacion.dto;

import java.time.LocalDate;
import java.util.List;
import com.tp.facturacion.model.TipoComprobante;

public record FacturaEdicionDto(Long id, Long revision, TipoComprobante tipoComprobante,
        Long puntoVentaId, Long clienteId, String clienteDenominacion, String clienteCuit,
        Long condicionIvaId, Long tipoMonedaId, Long listaPrecioId, LocalDate fechaEmision,
        Integer documentoReceptorTipo, Long documentoReceptorNumero,
        String observaciones, boolean puedeModificar, boolean puedeCambiarTipo,
        List<OpcionDto> puntosVentaHomologacion, List<Item> detalles) {

    public record Item(Long articuloId, Long listaPrecioArticuloId,
            String articuloCodigo, String articuloDenominacion, String listaPrecioCodigo,
            String listaPrecioDenominacion, String descripcion, double cantidad,
            double porcentajeBonificacion, double precioVenta, java.math.BigDecimal ivaPorcentaje) {}
}
