-- Datos sintéticos para una base nueva inicializada con db/bootstrap/demo_local.sql.
BEGIN;

INSERT INTO usuario (usuario, clave, nombre, apellido)
VALUES ('demo-local', 'sin-acceso', 'Usuario', 'Demostración');

INSERT INTO condicion_iva (codigoafip, denominacion, fechaalta, fechamodificacion, usuario_carga_id, usuario_modificacion_id)
VALUES (1, 'Responsable Inscripto', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1, 1),
       (5, 'Consumidor Final', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1, 1);

INSERT INTO punto_venta (numero, descripcion, fechaalta, fechamodificacion, usuario_carga_id, usuario_modificacion_id)
VALUES (1, 'Mostrador de prueba', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1, 1),
       (99001, 'Punto de homologación de ejemplo', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1, 1);

INSERT INTO tipo_moneda (codigoafip, denominacion, simbolo, fechaalta, fechamodificacion,
        usuario_carga_id, usuario_modificacion_id)
VALUES ('PES', 'Pesos argentinos', '$', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1, 1);

INSERT INTO lista_precio (codigo, denominacion, fechaalta, fechamodificacion, usuario_carga_id, usuario_modificacion_id)
VALUES ('GEN', 'General', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1, 1);

WITH articulos AS (
    INSERT INTO articulo (codigo, denominacion, fechaalta, fechamodificacion,
            usuario_carga_id, usuario_modificacion_id)
    VALUES ('DEMO-01', 'Cuaderno', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1, 1),
           ('DEMO-02', 'Bolígrafo', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1, 1),
           ('DEMO-03', 'Carpeta', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1, 1)
    RETURNING id, codigo
)
INSERT INTO lista_precio_articulo (articulo_id, lista_precio_id, precioventa, fechaalta,
        fechamodificacion, usuario_carga_id, usuario_modificacion_id)
SELECT a.id, lp.id, p.precio, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1, 1
FROM articulos a
JOIN (VALUES ('DEMO-01', 4500), ('DEMO-02', 1400), ('DEMO-03', 6700)) p(codigo, precio) USING (codigo)
CROSS JOIN lista_precio lp
WHERE lp.codigo = 'GEN';

COMMIT;
