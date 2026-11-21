package com.tp.facturacion.model;

public enum TipoComprobante {
    FACTURA_C,
    FACTURA_B,
    FACTURA_X,
    /** Alias de entrada para clientes de la API anteriores a la migración. */
    CONSUMIDOR_FINAL;

    public boolean esInterno() {
        return this == FACTURA_X || this == CONSUMIDOR_FINAL;
    }
}
