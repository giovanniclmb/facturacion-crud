# Edición de comprobantes y autorización manual

Sistema visual: [revisión 1](../../../design-system/README.md).
Approval: approved (2026-11-20, by user) · revisión 4

Las revisiones 2 y 3 fueron aprobadas el 20/11/2026.

[Abrir el formulario interactivo](index.html). Los datos y la respuesta de ARCA de la vista son ficticios.

- Una Factura C nueva o editada se guarda localmente. En Comprobantes, el usuario confirma el CUIT y pulsa **Informar a ARCA** para solicitar el CAE.
- Factura B interna a Factura C: el formulario pide cliente y condición de IVA. Si hace falta otro punto para homologar, permite elegir uno habilitado. Se conserva el registro y, si se cambia el punto, se asigna un número local en el destino.
- Factura C a Factura B interna: se permite antes del primer intento fiscal registrado. Durante la conversión se puede elegir cualquier punto de venta activo. Si cambia el punto se asigna un número local en el destino. El formulario oculta los datos del receptor.
- Al editar una Factura B interna, se puede elegir otro punto de venta activo y guardar con el mismo tipo. Si cambia el punto, se asigna el próximo número local del destino.
- Una Factura C rechazada sigue editable para corregir el cliente u otros datos, con el tipo C. El reintento también se inicia desde Comprobantes. Las autorizadas y las que tienen un trámite activo o incierto siguen bloqueadas.

- B a C: [escritorio](previews/B-a-C-escritorio.png) y [móvil](previews/B-a-C-movil.png).
- C a B: [escritorio](previews/C-a-B-escritorio.png) y [móvil](previews/C-a-B-movil.png).
- Edición de Factura B: [escritorio](previews/edicion-B-escritorio.png) y [móvil](previews/edicion-B-movil.png).
- Factura C guardada: [escritorio](previews/guardada-escritorio.png) y [móvil](previews/guardada-movil.png).

Preview: rendered (2026-11-20), 1440 × 900 y 390 × 844.

Visual verify (revisión 2): passed (2026-11-20), Chrome local a 390 × 844 y 1440 × 900. Se comprobó la conversión B a C, la selección de cliente y punto, el tipo bloqueado de una C rechazada y la confirmación manual desde Comprobantes. El formulario real conserva el campo Observaciones y la tabla editable de ítems. La comprobación visual no escribió datos ni solicitó CAE.

- Formulario aplicado: [escritorio](verify/B-a-C-escritorio.png) y [móvil](verify/B-a-C-movil.png).

Visual verify (revisión 3): passed (2026-11-20), Chrome local a 390 × 844 y 1440 × 900. Se comprobó el selector de puntos activos al pasar de C a B, el selector de puntos habilitados al pasar de B a C y la restauración del punto original al volver al tipo inicial. Se verificó el guardado y la numeración del destino con pruebas de integración. La comprobación visual se canceló sin guardar los comprobantes locales.

- Conversión de C a B aplicada: [escritorio](verify/C-a-B-escritorio.png) y [móvil](verify/C-a-B-movil.png).

Visual verify (revisión 4): passed (2026-11-20), Chrome local a 390 × 844 y 1440 × 900. El selector permite elegir otro punto activo al editar una B sin cambiar su tipo. Se verificó el guardado, el mismo ID y la numeración del destino en la prueba de integración. En la base habitual se canceló la edición luego de revisar el formulario.

- Edición de Factura B aplicada: [escritorio](verify/edicion-B-escritorio.png) y [móvil](verify/edicion-B-movil.png).
