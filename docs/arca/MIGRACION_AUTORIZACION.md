# Migración de autorización ARCA

La migración es aditiva, transaccional y de ejecución explícita una sola vez. No se ejecuta durante el arranque y no usa Flyway. No cambia números locales ni registros previos. Una repetición falla y revierte la transacción; no oculta estructuras incompatibles.

Con la aplicación detenida, inspeccionar el esquema:

```sh
psql --dbname=facturacion_crud --username=giovita -c '\d factura_venta'
```

Comprobar que no existan previamente las columnas fiscales ni las tablas `arca_serie` y `arca_intento`. Guardar antes una copia privada fuera del repositorio mediante `pg_dump`, con permisos 0600; no publicar su contenido.

```sh
psql --dbname=facturacion_crud --username=giovita -v ON_ERROR_STOP=1 -f db/migrations/001_arca_autorizacion.sql
psql --dbname=facturacion_crud --username=giovita -c '\d arca_intento'
psql --dbname=facturacion_crud --username=giovita -c '\d arca_serie'
```

Verificar índices parciales y claves foráneas, números locales y cantidad de facturas conservados. Iniciar con `spring.jpa.hibernate.ddl-auto=validate`. Esta guía no constituye evidencia de aplicación a una base real.

Pruebas PostgreSQL aisladas (URL y credenciales solo de una base de pruebas):

```sh
./mvnw -Parca-postgres -Dtest=MigracionArcaPostgresTests,ReservaArcaPostgresTests test
```

El perfil exige `ARCA_TEST_JDBC_URL`; acepta `ARCA_TEST_JDBC_USER` y `ARCA_TEST_JDBC_PASSWORD`. Cada caso crea un esquema aleatorio `arca_test_*`, nunca usa `public` ni datos reales y elimina exclusivamente ese esquema. Sin el perfil las pruebas se omiten explícitamente.

Para volver al código previo, conservar tablas y registros de intentos. Deshabilitar nuevas autorizaciones y reconciliar los pendientes. No borrar historial ni liberar INCIERTO por antigüedad. PREPARANDO puede transferir propiedad antes del envío; ENVIANDO e INCIERTO solo se consultan, sin reenvío automático. El rechazo confirmado permite corregir el borrador y crear un nuevo intento conservando el anterior.


## Aplicación local verificada

03/11/2026: migración aplicada transaccionalmente a la base local después de respaldo
privado en `Integrador/arca-homo/backups/` (archivo 0600). Conteo antes/después: 10 facturas;
suma de números locales 55 e importe total agregado 1185760, sin cambios.
Arranque y suite completa con ddl-auto=validate aprobados. No repetir la migración en
esa base: es one-shot y rechaza una segunda aplicación.

El puerto 8080 estaba ocupado por otro proyecto. El backend temporal de verificación usó
18080; no se detuvo ni reconfiguró el otro proyecto. El frontend conserva su URL habitual
8080: al ejecutar normalmente, elegir el puerto del proyecto y su URL de API concordante.
