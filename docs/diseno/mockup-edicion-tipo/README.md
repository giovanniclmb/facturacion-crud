# Edición de comprobantes y autorización manual

Sistema visual: [revisión 1](../../../design-system/README.md).
Approval: approved (2026-11-20, by user) · revisión 2

[Abrir el formulario interactivo](index.html). Los datos y la respuesta de ARCA de la vista son ficticios.

- Una Factura C nueva o editada se guarda localmente. En Comprobantes, el usuario confirma el CUIT y pulsa **Informar a ARCA** para solicitar el CAE.
- Factura B interna a Factura C: el formulario pide cliente y condición de IVA. Si hace falta otro punto para homologar, permite elegir uno habilitado. Se conserva el registro y, si se cambia el punto, se asigna un número local en el destino.
- Factura C a Factura B interna: se permite antes del primer intento fiscal registrado. El formulario oculta los campos del receptor y guarda el comprobante sin cliente.
- Una Factura C rechazada sigue editable para corregir el cliente u otros datos, con el tipo C. El reintento también se inicia desde Comprobantes. Las autorizadas y las que tienen un trámite activo o incierto siguen bloqueadas.

- B a C: [escritorio](previews/B-a-C-escritorio.png) y [móvil](previews/B-a-C-movil.png).
- C a B: [escritorio](previews/C-a-B-escritorio.png) y [móvil](previews/C-a-B-movil.png).
- Factura C guardada: [escritorio](previews/guardada-escritorio.png) y [móvil](previews/guardada-movil.png).

Preview: rendered (2026-11-20), 1440 × 900 y 390 × 844.

Visual verify: passed (2026-11-20), Chrome local a 390 × 844 y 1440 × 900. Se comprobó la conversión B a C, la selección de cliente y punto, el tipo bloqueado de una C rechazada y la confirmación manual desde Comprobantes. El formulario real conserva el campo Observaciones y la tabla editable de ítems. La comprobación visual no escribió datos ni solicitó CAE.

- Formulario aplicado: [escritorio](verify/B-a-C-escritorio.png) y [móvil](verify/B-a-C-movil.png).
