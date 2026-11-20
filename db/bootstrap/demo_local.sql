-- Ejecutar con psql sobre una base vacía. Las rutas se resuelven relativas a este archivo.
\set ON_ERROR_STOP on
\ir 000_esquema_base.sql
\ir ../migrations/001_arca_autorizacion.sql
\ir ../migrations/002_correcciones_facturacion.sql
\ir ../demo/catalogos_base.sql
\ir ../demo/clientes_arca.sql
\ir ../demo/precios_mayorista.sql
\ir ../demo/precios_costo.sql
