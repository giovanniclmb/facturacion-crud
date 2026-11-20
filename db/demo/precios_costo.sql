-- Completa Costo para los artículos activos con precios General y Mayorista.
-- Aplicar después de precios_mayorista.sql. Usa descuentos de demostración.
BEGIN;

LOCK TABLE lista_precio, lista_precio_articulo IN SHARE ROW EXCLUSIVE MODE;

INSERT INTO lista_precio (codigo, denominacion, fechaalta, fechamodificacion,
        usuario_carga_id, usuario_modificacion_id)
SELECT 'DEMO-COS', 'Costo', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1, 1
WHERE NOT EXISTS (
    SELECT 1 FROM lista_precio WHERE fechabaja IS NULL AND lower(trim(denominacion)) = 'costo'
);

WITH listas AS (
    SELECT
        min(id) FILTER (WHERE lower(trim(denominacion)) = 'general' OR lower(trim(denominacion)) LIKE 'lista general%') AS general,
        min(id) FILTER (WHERE lower(trim(denominacion)) = 'mayorista') AS mayorista,
        min(id) FILTER (WHERE lower(trim(denominacion)) = 'costo') AS costo
    FROM lista_precio
    WHERE fechabaja IS NULL
)
INSERT INTO lista_precio_articulo (articulo_id, lista_precio_id, precioventa, fechaalta,
        fechamodificacion, usuario_carga_id, usuario_modificacion_id)
SELECT a.id, l.costo, least(round(g.precioventa::numeric * 0.70, 2), trunc(m.precioventa::numeric * 0.90, 2)),
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1, 1
FROM articulo a CROSS JOIN listas l
JOIN LATERAL (
    SELECT p.precioventa FROM lista_precio_articulo p
    WHERE p.articulo_id = a.id AND p.lista_precio_id = l.general AND p.fechabaja IS NULL
    ORDER BY p.id DESC LIMIT 1
) g ON g.precioventa > 0
JOIN LATERAL (
    SELECT p.precioventa FROM lista_precio_articulo p
    WHERE p.articulo_id = a.id AND p.lista_precio_id = l.mayorista AND p.fechabaja IS NULL
    ORDER BY p.id DESC LIMIT 1
) m ON m.precioventa > 0
WHERE a.fechabaja IS NULL AND NOT EXISTS (
    SELECT 1 FROM lista_precio_articulo p
    WHERE p.lista_precio_id = l.costo AND p.articulo_id = a.id AND p.fechabaja IS NULL
);

COMMIT;
