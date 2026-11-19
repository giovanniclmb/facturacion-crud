# Ver comprobante y estado de ARCA en todas las filas

Sistema visual: [revisión 1 aprobada](../../../design-system/README.md).
Approval: approved (2026-11-18, by user)

[Abrir la propuesta](index.html). El selector de ejemplo muestra cómo se imprime una Factura C sin CAE.

- Cada fila ofrece **Ver comprobante** y **Estado de ARCA**, incluidas las históricas, anuladas, pendientes, rechazadas, autorizadas e internas.
- **Ver comprobante** abre un PDF imprimible. Con autorización comprobada, incluye los datos fiscales y QR. Sin una autorización comprobada, el PDF muestra el motivo o estado disponible y se identifica como copia interna sin validez fiscal.
- **Estado de ARCA** muestra el estado registrado. Un comprobante interno indica «No aplica». Si hay un resultado incierto, se mantiene la consulta a ARCA que ya existe, con una acción explícita.
- Una factura antigua que conserva un CAE local sin evidencia de autorización muestra «CAE histórico sin verificar». Abrir cualquiera de las dos acciones conserva los registros y no solicita un CAE.

## Verificación de la implementación

- [Escritorio](verify/acciones-escritorio.png) y [móvil](verify/acciones-movil.png), con datos de ejemplo. Los dos botones aparecen juntos. La grilla mantiene los controles de edición y las acciones fiscales existentes donde corresponden.
- Se abrió el PDF y se leyó el estado de ARCA de los 71 registros de la base habitual y de los 73 de la copia de demostración.
- Visual verify: passed (18/11/2026).
