# Correcciones de facturación pedidas por la cátedra

Aprobada por el usuario el 18/11/2026.

## Resultado esperado

Crear y editar comprobantes desde el facturador, elegir una lista de precios, consultar los comprobantes ordenados por fecha de emisión y abrir el PDF individual. La prueba fiscal demuestra una Factura C creada desde la interfaz con un CAE obtenido y luego consultado en ARCA homologación. La revisión visual 1 y los [mockups en escritorio y móvil](../../diseno/mockups-facturacion/README.md) fueron aprobados el 18/11/2026.

## Tipos de comprobante y datos existentes

- **Factura C.** Requiere un cliente del padrón. Conservar las validaciones vigentes de receptor, CUIT, condición de IVA y alcance fiscal de productos en pesos. El guardado solicita CAE desde el flujo explícito de creación o edición.
- **Consumidor final.** Comprobante interno sin cliente. No solicita CAE. La vista, los reportes y su PDF muestran «Consumidor final». El PDF se identifica como comprobante interno sin validez fiscal.
- Persistir un tipo explícito en nuevas operaciones. Mantener las facturas históricas identificadas como registros existentes, preservar sus relaciones y valores y evitar emisiones durante la migración. El guardado explícito de una edición de Factura C elegible puede iniciar la emisión. Las llamadas existentes de actualización de estado, como anular, no son un guardado fiscal.
- Validar las reglas de tipo en servidor y en el formulario. El esquema permite cliente nulo para el comprobante interno. La relación de cliente y los datos de facturas históricas se conservan.

## Guardado, edición y ARCA

- Reusar el servicio fiscal actual y sus estados e intentos persistidos. Validar el formulario y guardar en una transacción local. Ejecutar la autorización por fuera de esa transacción, con el ID de la factura guardada. Evitar una transacción abierta durante la red y conservar la confirmación del CUIT en la acción explícita de guardado fiscal.
- Comunicar el resultado del guardado y de la solicitud con el ID de factura. Una falla fiscal posterior al guardado debe permitir volver al registro existente y evitar que el usuario vuelva a crear la misma factura para corregirla.
- **Autorizada:** mostrar número fiscal, CAE y vencimiento, ofrecer el PDF y bloquear modificaciones. **Rechazada:** guardar el motivo, habilitar la edición y permitir otra solicitud al guardar la corrección. **Falla previa al envío:** mostrar que la factura quedó guardada, con opción de corregir o volver a guardar. **En curso o incierta:** bloquear edición, anulación, borrado y nuevos envíos. Conservar la acción de consulta y reconciliar por número, tipo y punto, contrastando los datos fiscales antes de adoptar el CAE.
- Mantener las reservas por serie y las garantías contra doble despacho de la integración. Proteger el envío de formulario ante doble clic. Si se pierde la respuesta, distinguir el estado local del fiscal y recuperar la factura existente antes de permitir otra emisión.
- Para rellenar la edición, incorporar una lectura de detalle con identificadores de catálogos e ítems. Usar el mismo formulario de alta y edición. Permitir editar pendientes y rechazadas sin CAE. Bloquear anuladas, autorizadas y solicitudes en curso o inciertas. Habilitar cambios en cliente, fecha, moneda, artículos, cantidades y descuentos. Mantener fijo el punto de venta.
- **Prueba de homologación:** verificar el punto 99001 antes de usarlo, confirmar que es exclusivo y seleccionar un cliente de prueba del padrón. Registrar resultado de emisión y consulta con datos de demostración, sin volcar credenciales ni documentos privados. Si el punto no se puede usar, informar la respuesta de ARCA y acordar otro punto exclusivo. La aprobación de pruebas locales por sí sola no acredita un CAE obtenido en homologación.

## Precios

- Mostrar una lista activa para toda la factura, con **General** como valor inicial. Consultar las opciones de `lista_precio` y los precios vigentes de `lista_precio_articulo`. Para cada artículo seleccionar el precio de la lista activa y, si falta, el de General con el origen identificado. Si faltan ambos precios, impedir guardar el ítem con mensaje visible.
- El servidor define el precio y recalcula importes al guardar. La edición cotiza todos los ítems a precio vigente, incluidos los no modificados. Una factura emitida conserva los importes guardados para consulta e impresión. Si una factura antigua carece de lista elegida, mostrar General como valor inicial de edición y resolver sus precios al guardar.
- Cargar únicamente los ejemplos Mayorista que falten para demostrar tanto el precio específico como la alternativa General. Mantener intactos los precios existentes.

## Orden y PDF

- Admitir ASC y DESC para fecha de emisión, con DESC inicial y el ID como desempate en la misma dirección. Usar la misma regla para grilla paginada, PDF de reportes y Excel. Volver a la primera página cuando se cambia el orden.
- **Ver comprobante** abre un PDF individual en línea para Factura C autorizada y para comprobante interno. El visor permite imprimir o descargar. La Factura C en A4 muestra emisor, receptor, ítems, importes, número fiscal, CAE, vencimiento, QR y leyenda de homologación. Usar datos fiscales persistidos para el QR y la representación autorizada. Los datos del emisor se configuran localmente.
- El reporte agregado mantiene su función. El PDF individual se genera desde los datos guardados, con paginación para comprobantes de varios ítems y respuesta de error clara si el estado no permite imprimir.

## Verificación de aceptación

1. Aplicar la migración en una base de prueba con facturas históricas y comparar cantidad de registros, importes y estados. Confirmar que la migración no genera intentos de CAE.
2. Crear un comprobante interno sin cliente, confirmar que no llega a ARCA y abrir su PDF. Rechazar Factura C sin cliente.
3. Crear Factura C en homologación con cliente de prueba y comprobar que la solicitud ocurre al guardar. Consultar ese mismo comprobante por WSFEv1 y contrastar CAE, punto de venta, número y vencimiento con la factura y su PDF.
4. Cubrir por pruebas automatizadas rechazo corregible, falla previa a despacho, respuesta incierta, doble guardado y reconciliación. Confirmar que un comprobante autorizado o con resultado incierto no es editable.
5. Probar General, Mayorista, alternativa General y ausencia de precio en ambas. Cambiar un precio vigente, editar y comprobar que se recalculan todos los ítems.
6. Confirmar en grilla, PDF y Excel el orden inicial y el inverso con fechas repetidas. Verificar que los filtros conservan su efecto en ambos órdenes.
7. Revisar las pantallas aprobadas en 390 × 844 y 1440 × 900, los PDF individuales y los estados de carga y error. Revisar consola y solicitudes fallidas durante el recorrido.
