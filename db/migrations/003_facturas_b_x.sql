BEGIN;

ALTER TABLE factura_venta
    DROP CONSTRAINT ck_factura_tipo_receptor,
    ADD COLUMN documento_receptor_tipo integer,
    ADD COLUMN documento_receptor_numero bigint;

UPDATE factura_venta
SET tipo_comprobante = 'FACTURA_X'
WHERE tipo_comprobante = 'CONSUMIDOR_FINAL';

ALTER TABLE factura_venta
    ADD CONSTRAINT ck_factura_tipo_receptor CHECK (
        CASE
            WHEN tipo_comprobante IS NULL OR tipo_comprobante = 'FACTURA_C' THEN cliente_id IS NOT NULL
            WHEN tipo_comprobante = 'FACTURA_B' THEN cliente_id IS NULL
            WHEN tipo_comprobante = 'FACTURA_X' THEN cliente_id IS NULL AND cae IS NULL
            ELSE FALSE
        END
    ),
    ADD CONSTRAINT ck_factura_documento_receptor CHECK (
        (documento_receptor_tipo IS NULL AND documento_receptor_numero IS NULL)
        OR (tipo_comprobante IS NOT NULL AND tipo_comprobante = 'FACTURA_B'
            AND documento_receptor_tipo IS NOT NULL AND documento_receptor_tipo IN (86, 87, 96)
            AND documento_receptor_numero IS NOT NULL AND documento_receptor_numero > 0)
    );

ALTER TABLE articulo
    ADD COLUMN iva_porcentaje numeric(5,2) CHECK (iva_porcentaje = 21);

ALTER TABLE factura_venta_detalle
    ADD COLUMN iva_porcentaje numeric(5,2) CHECK (iva_porcentaje = 21);

ALTER TABLE arca_serie
    DROP CONSTRAINT arca_serie_tipo_check,
    ADD CONSTRAINT arca_serie_tipo_check CHECK (tipo IN (6, 11));

ALTER TABLE arca_intento
    DROP CONSTRAINT arca_intento_doc_tipo_check,
    ADD CONSTRAINT arca_intento_documento_check CHECK (
        (doc_tipo = 99 AND doc_numero = 0)
        OR (doc_tipo IN (80, 86, 87, 96) AND doc_numero > 0)
    ),
    ADD COLUMN importe_neto numeric(18,2),
    ADD COLUMN importe_iva numeric(18,2),
    ADD CONSTRAINT arca_intento_importes_check CHECK (
        (importe_neto IS NULL AND importe_iva IS NULL)
        OR (importe_neto IS NOT NULL AND importe_iva IS NOT NULL
            AND importe_neto >= 0 AND importe_iva >= 0 AND importe_neto + importe_iva = total)
    );

COMMIT;
