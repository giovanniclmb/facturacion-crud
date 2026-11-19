BEGIN;

ALTER TABLE factura_venta
    ALTER COLUMN cliente_id DROP NOT NULL,
    ADD COLUMN tipo_comprobante varchar(32),
    ADD COLUMN lista_precio_id bigint REFERENCES lista_precio(id),
    ADD COLUMN operacion_guardado uuid UNIQUE,
    ADD COLUMN revision bigint NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_factura_tipo_receptor CHECK (
        CASE
            WHEN tipo_comprobante IS NULL OR tipo_comprobante = 'FACTURA_C' THEN cliente_id IS NOT NULL
            WHEN tipo_comprobante = 'CONSUMIDOR_FINAL' THEN cliente_id IS NULL AND cae IS NULL
            ELSE FALSE
        END
    );

CREATE INDEX ix_factura_lista_precio ON factura_venta(lista_precio_id);
CREATE INDEX ix_factura_fecha_id ON factura_venta(fechaemision, id);

COMMIT;
