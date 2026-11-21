-- Ejecutar únicamente en una base de demostración. Los precios ya cargados son finales.
BEGIN;

UPDATE articulo AS a
SET iva_porcentaje = 21
FROM (VALUES
    ('DEMO-01', 'Cuaderno'),
    ('DEMO-02', 'Bolígrafo'),
    ('DEMO-03', 'Carpeta'),
    ('ART001', 'Teclado mecanico'),
    ('ART002', 'Mouse inalambrico'),
    ('ART003', 'Pad para mouse'),
    ('ART004', 'Monitor 27 pulgadas'),
    ('ART005', 'Licuadora'),
    ('ART006', 'Cable HDMI generico')
) AS demo(codigo, denominacion)
WHERE a.codigo = demo.codigo AND a.denominacion = demo.denominacion
    AND a.fechabaja IS NULL AND a.iva_porcentaje IS NULL;

COMMIT;
