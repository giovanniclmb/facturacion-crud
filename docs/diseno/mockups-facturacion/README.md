# Mockups de las correcciones de facturación

Sistema visual: [revisión 1 aprobada](../../../design-system/README.md).
Approval: approved (2026-11-18, by user)

Abrir [index.html](index.html) en un navegador y usar la navegación de la maqueta para recorrer las pantallas. Los datos, resultados de ARCA y QR son de ejemplo. El selector de orden muestra el estado inicial; en la aplicación actualizará la grilla y el orden de los reportes.

- **Comprobantes.** El orden inicial es por fecha descendente y el desempate por ID sigue la dirección seleccionada. Los estados determinan las acciones disponibles. Una respuesta incierta permite consultar el resultado.
- **Alta de Factura C.** El cliente del padrón es obligatorio. Guardar solicita CAE automáticamente. El resultado queda visible en la grilla.
- **Alta de Consumidor final.** Guarda un comprobante interno sin cliente ni solicitud de CAE.
- **Edición.** Los pendientes y rechazados sin CAE se pueden corregir. Al guardar se recalculan los precios, se conserva el punto de venta y se solicita CAE si la edición de Factura C es elegible.
- **Buscador.** La lista se elige para toda la factura. Abre en General y muestra el origen del precio cuando se usa General como alternativa.
- **Comprobantes individuales.** La acción Ver comprobante abre el PDF de los autorizados y de los internos. El comprobante autorizado de ejemplo muestra los campos del A4 de homologación. El interno se identifica como tal.

El flujo de autorización reutiliza la persistencia y conciliación existentes, con la factura guardada antes de la comunicación remota. Un rechazo permite corregir y reenviar. Si queda incertidumbre sobre el resultado, el comprobante permanece protegido mientras se consulta a ARCA. Las facturas anteriores se conservan y solo se intenta autorizarlas al guardar explícitamente una edición de Factura C elegible. La prueba de aceptación requiere obtener y consultar un CAE real de homologación para una Factura C en pesos y cliente de prueba, con punto 99001 si ARCA lo admite.

## Capturas

Los mockups se renderizaron en Chrome con áreas visibles de 1440 × 900 y 390 × 844. Las capturas incluyen el contenido de toda la página.

| Pantalla | Escritorio | Móvil |
| --- | --- | --- |
| Comprobantes | [PNG](previews/listado-escritorio.png) | [PNG](previews/listado-movil.png) |
| Nueva Factura C | [PNG](previews/alta-escritorio.png) | [PNG](previews/alta-movil.png) |
| Consumidor final | [PNG](previews/consumidor-escritorio.png) | [PNG](previews/consumidor-movil.png) |
| Edición tras rechazo | [PNG](previews/edicion-escritorio.png) | [PNG](previews/edicion-movil.png) |
| Buscador en General | [PNG](previews/articulos-escritorio.png) | [PNG](previews/articulos-movil.png) |
| Buscador en Mayorista | [PNG](previews/mayorista-escritorio.png) | [PNG](previews/mayorista-movil.png) |
| Factura C autorizada | [PNG](previews/pdf-escritorio.png) | [PNG](previews/pdf-movil.png) |
| Comprobante interno | [PNG](previews/interno-escritorio.png) | [PNG](previews/interno-movil.png) |

## Verificación de la implementación

Verificación visual del 18/11/2026 en Chrome a 1440 × 900 y 390 × 844.
Se usaron datos de ejemplo para la grilla y el rechazo, y el catálogo local
para el buscador. Durante el recorrido no se observaron excepciones de JavaScript
ni solicitudes fallidas de la API.

| Pantalla | Escritorio | Móvil |
| --- | --- | --- |
| Comprobantes | [PNG](verify/listado-escritorio.png) | [PNG](verify/listado-movil.png) |
| Nueva Factura C | [PNG](verify/alta-escritorio.png) | [PNG](verify/alta-movil.png) |
| Consumidor final | [PNG](verify/consumidor-escritorio.png) | [PNG](verify/consumidor-movil.png) |
| Edición tras rechazo | [PNG](verify/edicion-escritorio.png) | [PNG](verify/edicion-movil.png) |
| Buscador en General | [PNG](verify/general-escritorio.png) | [PNG](verify/general-movil.png) |
| Buscador en Mayorista | [PNG](verify/mayorista-escritorio.png) | [PNG](verify/mayorista-movil.png) |

Se abrió el PDF autorizado de homologación, se renderizó en A4 y se contrastó
el QR con el registro. También se abrió el PDF individual de un comprobante
interno creado y editado desde la interfaz. El PDF que contiene el CUIT real
del emisor queda fuera del repositorio.

Visual verify: differences (18/11/2026).

- **Menor, escritorio y móvil.** La aplicación integra las vistas en los paneles
  plegables y abre el buscador en el diálogo existente. La maqueta mostraba
  esos formularios y la búsqueda como vistas independientes. Para comparar,
  desplegar Nueva factura o Buscar artículo en un ítem.
- **Menor, PDF A4.** La implementación agrega columnas de precio unitario y
  bonificación y reúne los datos de cabecera en una columna. La maqueta
  mostraba una cabecera repartida en dos columnas.
