# Plan de implementación de las correcciones de facturación

**Objetivo**
Completar las seis correcciones y demostrar una Factura C con CAE consultable en ARCA homologación.

**Arquitectura**
El backend concentra la validación, la selección de precio y las transiciones fiscales. El guardado local y la comunicación fiscal se coordinan en pasos independientes mediante la autorización y conciliación existentes. La interfaz usa el formulario, los exploradores y la grilla actuales como puntos de entrada.

**Stack**
Java 21, Spring Boot, JPA, PostgreSQL, OpenPDF, Apache POI, TypeScript y pruebas con Maven y Node.

**Fuentes**
[Especificación aprobada](../specs/2026-11-18-correcciones-facturacion-design.md) · [Mockups aprobados](../../diseno/mockups-facturacion/README.md) · [Guía ARCA](../../arca/HOMOLOGACION.md)

## Condiciones globales

- Consumidor final interno sin cliente ni CAE. Factura C con cliente del padrón y solicitud automática desde un guardado explícito.
- Emisión exclusivamente en homologación. Conservar reserva por serie, número local, auditoría y conciliación de respuestas inciertas.
- Preservar registros históricos y la compatibilidad de las operaciones parciales existentes. El cambio de estado por anulación no inicia un pedido fiscal.
- Punto de venta fijo al editar. Recalcular ítems a precio vigente de la lista de la factura, con General como alternativa identificada.
- Fecha de emisión descendente por defecto, ascendente opcional. ID como desempate y misma dirección para grilla y reportes.
- PDF individual para autorizados e internos. Verificación visual de las pantallas a 390 × 844 y 1440 × 900.

## Casos que guían la revisión

- La nueva migración conserva las facturas e intentos históricos y habilita un comprobante interno sin cliente.
- Un precio mayorista desactivado antes de guardar se resuelve en servidor con General vigente.
- Un guardado con respuesta perdida recupera la misma factura. Una edición stale o un intento fiscal activo no se pisan.
- Un CAE de homologación solo se adopta si la consulta coincide con el comprobante guardado.
- La impresión de una factura autorizada usa importes guardados incluso si el catálogo cambió después. Una factura rechazada no expone una representación fiscal.

## 1. Tipos, migración y edición base

**Archivos:** `db/migrations/002_correcciones_facturacion.sql`, `FacturaVenta.java`, `FacturaRequestDto.java`, `FacturaService.java`, `FacturaRestController.java`, DTO de detalle e integración PostgreSQL.

- [ ] Escribir casos que creen un interno sin cliente, rechacen una C explícita sin cliente, carguen el detalle necesario para editar y verifiquen que una factura histórica retiene IDs, totales y estado tras migrar.
- [ ] Ejecutar `./mvnw -Dtest=FacturaCrudConsignasTests,FacturaCaeInmutableTests,CorreccionesFacturaTests test`. Confirmar el fallo de los casos nuevos antes de cambiar la implementación. Ejecutar la migración también contra un esquema PostgreSQL aislado del entorno de demostración.
- [ ] Agregar el tipo explícito `FACTURA_C` / `CONSUMIDOR_FINAL`, la referencia opcional a la lista seleccionada y la nulabilidad de cliente para internos. Los registros anteriores conservan sus valores y su ausencia de tipo nuevo hasta un guardado explícito. Validar el tipo y aplicar el IVA de consumidor final por código de catálogo en el flujo interno. Mantener la compatibilidad de llamadas legacy sin tipo.
- [ ] Incorporar `GET /api/facturas/{id}/detalle` para el formulario. Devuelve tipo, IDs de catálogos, fecha, observaciones, selección de lista, artículos, cantidades, bonificaciones e importes guardados. Conservar `GET /api/facturas/{id}` como resumen. Validar en servidor que el punto de venta quede fijo y que los estados bloqueados no admitan edición.
- [ ] Repetir las pruebas. Comparar conteos, IDs, importes, estados y cantidad de intentos antes y después de la migración. Actualizar los pasos locales de aplicación de la migración con validación de esquema.

## 2. Lista por factura y resolución de precios

**Archivos:** `ListaPrecioArticuloRepository.java`, `CatalogoExplorerService.java`, `CatalogoExplorerController.java`, `FacturaService.java`, `CatalogoExplorerTests.java`, `CorreccionesFacturaTests.java`.

- [ ] Agregar casos para precio de la lista seleccionada, alternativa General, ambas sin precio, precio de baja y reemplazo del precio entre búsqueda y guardado. Incluir una edición sin cambios de ítems que recalcula todos los importes.
- [ ] Ejecutar `./mvnw -Dtest=CatalogoExplorerTests,CorreccionesFacturaTests test` y comprobar el fallo esperado.
- [ ] Exponer las listas activas y aceptar `listaPrecioId` al consultar el explorador. Resolver un precio vigente por artículo en la lista seleccionada y usar General cuando falte. Exponer el origen real para que la UI identifique la alternativa. Ordenar de forma estable y aplicar la misma vigencia en búsqueda y escritura.
- [ ] En los guardados explícitos, resolver todos los precios en servidor usando los IDs de artículos y la lista de cabecera. Preservar la selección por fila para los consumidores legacy del contrato. Cargar para la demo los datos Mayorista que falten y conservar un artículo que ejercite la alternativa General.
- [ ] Repetir pruebas y contrastar los totales devueltos con los ítems persistidos, incluida la edición de una factura anterior.

## 3. Guardado explícito y autorización fiscal

**Archivos:** `FacturaRestController.java`, `FacturaService.java`, servicio orquestador de guardado, `FacturaArcaController.java`, `AutorizacionArcaService.java`, `AlmacenIntentosArca.java`, `SnapshotFiscalFactory.java`, pruebas de integración fiscal y CRUD.

- [ ] Agregar integración con transporte simulado para alta autorizada, interno sin llamada remota, rechazo seguido de corrección, excepción anterior al envío, timeout posterior, pérdida de respuesta de guardado y dos envíos del mismo formulario. Cubrir anulación y facturas históricas sin guardado explícito.
- [ ] Ejecutar `./mvnw -Dtest=CorreccionesFacturaTests,AutorizacionArcaIntegracionTests,ReservaArcaTests,FacturaArcaControllerTests test` y observar los fallos nuevos.
- [ ] Identificar el guardado fiscal explícito en API y exigir confirmación del CUIT en su formulario. Asociar una clave de operación estable al alta enviada para que un reintento recupere la factura existente. Para edición, contrastar la versión leída y recuperar el estado por ID antes de repetir si se perdió la respuesta.
- [ ] Ejecutar primero el guardado local y, tras su commit, pedir CAE sobre el ID resultante. Devolver ese ID con el resultado fiscal aun cuando el pedido remoto falle después del guardado. Mantener las operaciones legacy y sus códigos de respuesta para quienes no usen el guardado fiscal.
- [ ] Ajustar elegibilidad fiscal y permisos de edición. Los internos no entran en la autorización. Reusar la reserva, el envío con numeración fiscal y `FECompConsultar`. Conservar el bloqueo si la respuesta es incierta y el motivo si ARCA rechaza. Una nueva edición elegible puede dar origen a un intento nuevo.
- [ ] Repetir pruebas y comprobar que el envío simulado ocurre fuera de la transacción del guardado, que el mecanismo de reserva limita despachos concurrentes y que el camino incierto consulta la misma serie y número.

## 4. Orden compartido y PDF individual

**Archivos:** `FacturaFiltroDto.java`, `FacturaService.java`, `FacturaRestController.java`, exportador individual en `reporte/`, `FacturaBusquedaTests.java`, `ReportesArcaTests.java`, pruebas de PDF individual.

- [ ] Agregar casos de fechas repetidas, ASC y DESC con paginación, exportación con el orden seleccionado, impresión de interno y autorizada en A4, rechazo al abrir un comprobante fiscal sin CAE y un comprobante de varias páginas.
- [ ] Ejecutar `./mvnw -Dtest=FacturaBusquedaTests,ReportesArcaTests,ComprobantePdfTests test` y observar los fallos nuevos.
- [ ] Agregar `ordenFecha=ASC|DESC` a los filtros compartidos, con DESC por defecto. Construir la cláusula JPQL a partir de la opción validada y usar `fechaEmision` e `id` con la misma dirección. Aplicarla antes de paginar o exportar.
- [ ] Exponer `GET /api/facturas/{id}/comprobante` con `Content-Type: application/pdf` y disposición `inline`. Diseñar el documento con OpenPDF y reutilizar los datos persistidos del intento autorizado para identificación fiscal y QR. Obtener emisor de configuración local y etiquetar homologación e interno según los mockups. No reutilizar el reporte agregado para el PDF individual.
- [ ] Repetir pruebas. Extraer texto de los PDF de prueba, comprobar tamaño A4 y leer el QR generado para contrastar número fiscal y CAE. Verificar en el visor un documento con varias páginas.

## 5. Aplicar los mockups a la interfaz

**Archivos:** `frontend/public/index.html`, `frontend/public/css/app.css`, `frontend/src/app.ts`, `frontend/src/components/AutorizacionArca.ts`, modelos DTO y permisos, servicios Factura y Catálogo, `frontend/tests/`.

- [ ] Agregar pruebas de render y eventos para tipo interno sin cliente, cliente obligatorio y CUIT confirmado en C, estado de carga, edición con punto fijo, alternativa General, cambios en orden de exportación y permisos de consulta e impresión. Ejecutar `cd frontend && npm test` y confirmar los fallos nuevos.
- [ ] Reusar el formulario para altas y ediciones con la lectura de detalle. La elección de lista vive en la factura y el explorador informa el origen real del precio. Conservar el ID y estado de la factura tras guardar o consultar. Deshabilitar el envío mientras está en curso y utilizar la clave de operación si se reintenta un alta sin respuesta.
- [ ] Incorporar el selector de orden en Comprobantes, reiniciar la página al cambiarlo, incluir ese valor en PDF y Excel y abrir el PDF individual desde **Ver comprobante**.
- [ ] Repetir `npm test`. Con frontend y API en ejecución, comparar los estados implementados con los mockups en ambos tamaños. Revisar consola, respuestas de red y foco por teclado. Guardar capturas de verificación junto a las aprobadas.

## 6. Integración final y aceptación en homologación

- [ ] Ejecutar `./mvnw test -Dspring.jpa.hibernate.ddl-auto=validate` y `cd frontend && npm test`. Habilitar el fixture PostgreSQL con `ARCA_TEST_JDBC_URL` y credenciales del esquema de prueba disponibles en el entorno local. Registrar fallos y pruebas omitidas.
- [ ] Revisar el flujo fiscal completo y resolver hallazgos antes de emitir. Verificar diagnóstico de homologación y disponibilidad del punto exclusivo 99001. Acordar el registro del cliente de prueba del padrón si todavía falta identificarlo.
- [ ] Guardar desde la interfaz una Factura C nueva en pesos con el cliente de prueba. Recuperar el comprobante por `FECompConsultar` y comparar número fiscal, punto, CAE y vencimiento con lo persistido. Abrir su PDF y verificar el QR. Si el resultado queda incierto, consultar el mismo número. Si el punto no permite emitir, registrar la causa y acordar otro punto exclusivo antes de continuar.
- [ ] Crear un comprobante interno desde la interfaz, confirmar ausencia de intento fiscal, imprimirlo y comprobar el orden reversible del listado y ambos reportes. Actualizar la guía local con el procedimiento verificado y el resultado de homologación sin exponer credenciales.
