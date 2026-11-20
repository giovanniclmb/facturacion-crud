-- Receptores de prueba para ARCA homologación. Usan el CUIT del ejemplo de WSFEv1.
BEGIN;

LOCK TABLE cliente IN SHARE ROW EXCLUSIVE MODE;

DO $carga_demo$
DECLARE
    cuit_demo CONSTANT varchar := '30000000007';
    nombre_demo varchar;
    contacto_demo bigint;
    domicilio_demo bigint;
    otros_ficticios CONSTANT varchar[] := ARRAY['Distribuidora Mendoza SA', 'Juan Lopez', 'Maria Gomez'];
BEGIN
    FOREACH nombre_demo IN ARRAY ARRAY['Cliente demostración ARCA', 'Giovanni Colombo (demo)'] || otros_ficticios
    LOOP
        -- Una coincidencia de nombre en una base existente puede ser un cliente real.
        IF nombre_demo = ANY(otros_ficticios)
                AND EXISTS (SELECT 1 FROM cliente WHERE denominacion = nombre_demo) THEN
            CONTINUE;
        END IF;

        IF EXISTS (
            SELECT 1 FROM cliente
            WHERE denominacion = nombre_demo
              AND (cuitcuil IS DISTINCT FROM cuit_demo OR fechabaja IS NOT NULL)
        ) THEN
            RAISE EXCEPTION 'El cliente de demostración "%" ya existe con otro documento o está dado de baja', nombre_demo;
        END IF;

        IF EXISTS (SELECT 1 FROM cliente WHERE denominacion = nombre_demo) THEN
            CONTINUE;
        END IF;

        INSERT INTO contacto DEFAULT VALUES RETURNING id INTO contacto_demo;
        INSERT INTO domicilio (nombrecalle, numerocalle)
        VALUES ('Domicilio de prueba', '1') RETURNING id INTO domicilio_demo;

        INSERT INTO cliente (
            cuitcuil, denominacion, contacto_id, domicilio_id, fechaalta, fechamodificacion,
            usuario_carga_id, usuario_modificacion_id
        )
        VALUES (
            cuit_demo, nombre_demo, contacto_demo, domicilio_demo, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1, 1
        );
    END LOOP;
END;
$carga_demo$;

COMMIT;
