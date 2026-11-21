# Factura B fiscal y Factura X interna

Sistema visual: [revisión 1](../../../design-system/README.md).
Approval: approved (2026-11-21, by user) · revisión 1

[Abrir la vista previa interactiva](index.html). Todas las vistas usan datos ficticios.

| Vista | Comportamiento |
| --- | --- |
| Factura B, consumidor final | Se guarda sin cliente registrado. Debajo de $10.000.000 el documento es opcional. El precio de lista es el importe final y se muestra el IVA que contiene. |
| Factura B, documento obligatorio | Desde $10.000.000, se pide tipo y número de documento directamente en la factura. El formulario conserva el total y no crea un registro de cliente. |
| Factura X | Comprobante interno sin cliente ni CAE. Se puede editar, cambiar de punto y abrir el PDF. |
| Comprobantes | Guardar una B deja la factura pendiente. La acción **Informar a ARCA** solicita el CAE manualmente cuando el emisor configurado puede emitir B. |
| PDF individual | B autorizada con número fiscal, IVA contenido, CAE y QR. X con número local y leyenda **Sin validez fiscal**. |

## Diseño propuesto

- Incorporar tipos explícitos `FACTURA_B` y `FACTURA_X`. Migrar únicamente las B internas (`CONSUMIDOR_FINAL`) a X, incluidas las anuladas. Conservar sus IDs, números, puntos, estados y detalles. El PDF de un comprobante migrado se identifica como X al volver a abrirlo. Las Facturas C y los intentos fiscales se conservan.
- Un emisor por instalación. Las Facturas B se guardan pendientes y solo se pueden enviar cuando está configurado un responsable inscripto para ese punto. La autorización de las C conserva el perfil fiscal compatible. Un cambio de emisor no autoriza ni consulta por otro CUIT los intentos de un emisor anterior.
- Usar WSFEv1 con serie de B separada de C. Para la B anónima se usa condición IVA Consumidor Final, tipo de documento 99 y número 0. En importes que obliguen a identificar, el documento se guarda en la factura y se valida antes de autorizar. El usuario confirma el receptor desde la grilla con la revisión vigente.
- Cargar la alícuota del 21 % solo en los artículos de demostración acordados. Para una B se exige tratamiento de IVA explícito. Con el precio de lista final, se calcula el neto y el IVA incluido, se conservan la alícuota e importes fiscales en el detalle y se verifican los totales en el envío y la recuperación.
- Una B o C sin primer intento fiscal puede convertirse a X. X puede convertirse a B o C con un punto habilitado para homologar. Las rechazadas conservan su clase pero pueden corregirse y enviarse de nuevo manualmente. Las autorizadas y los intentos activos o inciertos siguen bloqueados para edición.
- Verificar la migración en una base de pruebas, el guardado local, los importes justo por debajo y en el umbral, los payloads y respuestas de WSFE, la recuperación por la serie correcta, la inmutabilidad y los PDF. Para verificar un CAE real de B se necesita un emisor responsable inscripto habilitado en homologación.

## Fuentes

[clases de comprobantes de ARCA](https://arca.gob.ar/facturacion/regimen-general/), [datos del consumidor final](https://www.arca.gob.ar/fe/emision-autorizacion/datos-comprobantes.asp), [WSFEv1](https://www.arca.gob.ar/ws/documentacion/manuales/manual-desarrollador-ARCA-COMPG.pdf), [Régimen de Transparencia Fiscal](https://biblioteca.arca.gob.ar/search/query/norma.aspx?p=t%3ARAG%7Cn%3A5614%7Co%3A9%7Ca%3A2024%7Cf%3A12%2F12%2F2024).

## Capturas

Preview: rendered (2026-11-21), Chrome a 390 × 844 y 1440 × 900.

| Vista | Escritorio | Móvil |
| --- | --- | --- |
| B sin documento | [Abrir](previews/b-escritorio.png) | [Abrir](previews/b-movil.png) |
| B con documento | [Abrir](previews/b-documento-escritorio.png) | [Abrir](previews/b-documento-movil.png) |
| Edición X | [Abrir](previews/x-escritorio.png) | [Abrir](previews/x-movil.png) |
| Comprobantes | [Abrir](previews/comprobantes-escritorio.png) | [Abrir](previews/comprobantes-movil.png) |
| PDF de B | [Abrir](previews/pdf-b-escritorio.png) | [Abrir](previews/pdf-b-movil.png) |
| PDF de X | [Abrir](previews/pdf-x-escritorio.png) | [Abrir](previews/pdf-x-movil.png) |

## Verificación de la implementación

Visual verify: passed (2026-11-21). En Chrome local a 390 × 844 y 1440 × 900 se revisaron la X migrada, la B sin documento y el requisito de identificación desde el umbral. El formulario implementado conserva los campos de moneda, fecha y observaciones y el editor de ítems. Al informar una B identificada, la confirmación muestra el documento de la revisión vigente.

- Formulario B aplicado: [escritorio](verify/factura-b-escritorio.png) y [móvil](verify/factura-b-movil.png).
- En una copia de PostgreSQL se preservaron los datos de las cuatro B internas reclasificadas y se verificaron el guardado de B y X, el paso de X a B y la B con documento. En la base habitual se aplicó la migración con respaldo y se comprobó la disponibilidad del PDF de X y de C autorizada.
- Se revisaron los PDF de B y X con datos de prueba y se decodificó el QR de B. Las pruebas integradas con SOAP simulado cubren autorización y recuperación sin reenvío.
- La verificación inicial pasó 253 pruebas de backend con PostgreSQL y 50 de frontend. Luego se obtuvo y consultó el CAE de una Factura B a consumidor final sin documento en homologación real.
- El perfil de prueba `HOMOLOGACION_B_Y_C` permite solicitar CAE manualmente para ambas clases desde la misma sesión con un emisor habilitado en homologación. Cada clase conserva su propia serie.
