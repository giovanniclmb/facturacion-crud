-- Datos de demostración. Conserva el catálogo y los precios ya cargados.
BEGIN;

LOCK TABLE lista_precio, lista_precio_articulo IN SHARE ROW EXCLUSIVE MODE;

INSERT INTO lista_precio (codigo, denominacion, fechaalta, fechamodificacion, usuario_carga_id, usuario_modificacion_id)
SELECT 'DEMO-MAY', 'Mayorista', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1, 1
WHERE NOT EXISTS (
    SELECT 1 FROM lista_precio WHERE fechabaja IS NULL AND lower(trim(denominacion)) = 'mayorista'
);

WITH general AS (
    SELECT id FROM lista_precio
    WHERE fechabaja IS NULL AND (lower(trim(denominacion)) = 'general' OR lower(trim(denominacion)) LIKE 'lista general%')
    ORDER BY id LIMIT 1
),
mayorista AS (
    SELECT id FROM lista_precio
    WHERE fechabaja IS NULL AND lower(trim(denominacion)) = 'mayorista'
    ORDER BY id LIMIT 1
),
muestra AS (
    SELECT DISTINCT ON (p.articulo_id) p.articulo_id, p.precioventa
    FROM lista_precio_articulo p
    JOIN articulo a ON a.id = p.articulo_id AND a.fechabaja IS NULL
    JOIN general g ON g.id = p.lista_precio_id
    WHERE p.fechabaja IS NULL AND p.precioventa > 0
    ORDER BY p.articulo_id, p.id DESC LIMIT 2
)
INSERT INTO lista_precio_articulo (articulo_id, lista_precio_id, precioventa, fechaalta,
        fechamodificacion, usuario_carga_id, usuario_modificacion_id)
SELECT m.articulo_id, l.id, round(m.precioventa::numeric * 0.85, 2),
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1, 1
FROM muestra m CROSS JOIN mayorista l
WHERE NOT EXISTS (
    SELECT 1 FROM lista_precio_articulo p
    WHERE p.lista_precio_id = l.id AND p.articulo_id = m.articulo_id AND p.fechabaja IS NULL
);

COMMIT;
