# Revisión de UI y performance — 30/10/2026

## Alcance y evidencia

Revisión del frontend TypeScript/Bootstrap existente, sin cambios de producto en esta
etapa. Inspección de código y navegador con API ficticia, en escritorio y viewport de
390 × 844. Las medidas corresponden a ese entorno; no son resultados de producción.
El modelo y las reglas de facturación quedan fuera de esta propuesta, incluida ARCA.

La interfaz expresa el flujo del sistema: filtros, alta con ítems y comprobantes.
Los paneles plegables y explorers tienen propósito concreto y se conservan. El detector
Impeccable no produjo hallazgos mecánicos (`[]`); eso no cubre contraste ni comportamiento.

## Evaluación orientativa

| Dimensión | Puntos / 4 | Evidencia principal |
| --- | ---: | --- |
| Accesibilidad | 2 | Contraste insuficiente en Anular; foco y diálogo nativos ya presentes |
| Performance | 2 | Totales redundantes al renderizar; arranque secuencial |
| Responsive | 2 | Sin desbordamiento de página; tablas anchas y controles de fila pequeños |
| Consistencia visual | 3 | Bootstrap y variables existentes; algunos colores y tamaños aislados |
| Integridad de implementación | 3 | Flujos reales y coherentes; falta proteger acciones durante solicitudes |
| **Total** | **12 / 20** | Mejoras localizadas necesarias |

Esta puntuación es una rúbrica de revisión, no una certificación WCAG ni un benchmark.
No se evaluó modo oscuro, que no forma parte del requisito actual.

## Hallazgos priorizados

### P1 — Contraste del botón Anular

- Ubicación: `frontend/src/app.ts:438`, clase `btn-outline-warning`.
- Medido: texto `rgb(255,193,7)`, 14px, sobre celda blanca. Contraste calculado mediante
  luminancia relativa sRGB: **1,63:1**. El botón Eliminar, en el mismo fondo, alcanza 4,53:1.
- Impacto: el texto de una acción habilitada no alcanza el mínimo 4,5:1 para texto normal.
- Cambio propuesto: texto y borde con contraste suficiente; conservar la distinción entre
  acción normal y destructiva. Los controles deshabilitados se evalúan por separado.
- Comando sugerido: `$impeccable colorize`.

### P2 — Controles de fila pequeños

- Ubicación: `frontend/src/app.ts:224`, `:284`, `:438`, `:444`.
- Medido: botones Anular/Eliminar e inputs de cantidad/bonificación de **31px de alto**.
- Impacto: uso táctil menos cómodo. Se propone 44px; no se afirma que todo control menor
  de 44px incumpla por sí mismo el criterio AA de tamaño de objetivo.
- Cambio propuesto: mayor área de interacción, espaciado y tipografía numérica consistente.
- Comando sugerido: `$impeccable adapt`.

### P2 — Cantidad inicial marcada inválida

- Ubicación: `frontend/src/app.ts:272`.
- Medido: valor `1`, mínimo `0.01`, paso `1`; `validity.valid === false`. El navegador
  propone `0.01` y `1.01` como valores próximos válidos.
- Impacto: las flechas de incremento parten de una secuencia que no corresponde al valor
  inicial. El formulario usa validación propia; esto no demuestra que un alta de 1 falle.
- Cambio propuesto: alinear mínimo y paso con cantidades positivas de dos decimales.
- Comando sugerido: `$impeccable harden`.

### P2 — Totales redundantes al reconstruir ítems

- Ubicación: `frontend/src/app.ts:217`, `:242`, `:301`, `:305`.
- Evidencia de código: construir cada fila invoca `actualizarTotal`, que recorre todos
  los ítems. Un render de n filas ejecuta n+1 recorridos globales, además de los subtotales
  individuales. La parte de cálculo crece O(n²); se reconstruye también el DOM de las filas.
- Impacto esperado: trabajo evitable al añadir/quitar muchos renglones. No se midió aún
  una demora perceptible ni se atribuye toda la duración del render a los cálculos.
- Cambio propuesto: inicializar subtotales por fila y calcular el total una vez por render;
  conservar la actualización local al editar. Medir con 50/100/200 ítems antes y después.
- Comando sugerido: `$impeccable optimize`.

### P2 — El listado espera a los catálogos

- Ubicación: `frontend/src/app.ts:622`.
- Evidencia: `iniciar` espera `cargarCatalogos` antes de inicializar el borrador y solicitar
  las facturas. El listado no depende de estos catálogos.
- Impacto: una demora de catálogo retrasa el listado y la inicialización tardía puede
  reiniciar un borrador que el usuario haya comenzado mientras esperaba.
- Cambio propuesto: inicializar el formulario inmediatamente e iniciar catálogos y listado
  en paralelo, con estados de carga y errores independientes. Medir con latencia controlada.
- Comando sugerido: `$impeccable optimize`.

### P2 — Acciones disponibles durante solicitudes

- Ubicación: `frontend/src/app.ts:487`, `:521`, `:539`.
- Evidencia: la recarga conserva las filas anteriores habilitadas; las mutaciones no tienen
  control de operación en curso por factura.
- Impacto: acciones sobre resultados anteriores y solicitudes duplicadas mientras llega
  la respuesta. El backend mantiene la validación de negocio, incluido el bloqueo por CAE.
- Cambio propuesto: deshabilitar acciones durante recarga/mutación y evitar duplicados,
  con reactivación al fallar y sin bloquear permanentemente filas.
- Comando sugerido: `$impeccable harden`.

### P2 — Tablas extensas en móvil

- Ubicación: `frontend/public/index.html`, tablas de alta y listado.
- Medido a 390px: listado **811px** de contenido en **364px** visibles; ítems **599px** en
  **332px** visibles. La página completa conserva 390px: el desplazamiento está contenido.
- Impacto: hay que desplazarse para consultar datos y alcanzar acciones.
- Cambio propuesto: agrupar datos relacionados del listado sin perder información,
  mejorar tamaños/espaciado de ítems y dar una indicación del desplazamiento cuando exista.
- Comando sugerido: `$impeccable adapt`.

### P2 — Desplazamiento animado no respeta reducción de movimiento

- Ubicación: `frontend/src/app.ts:169`.
- Evidencia: `scrollIntoView` usa siempre `behavior: 'smooth'`.
- Cambio propuesto: desplazamiento inmediato cuando el usuario solicite reducir movimiento.
- Comando sugerido: `$impeccable harden`.

### P3 — Falta un landmark principal

- Ubicación: `frontend/public/index.html:28`.
- Evidencia: el contenedor raíz es un `div`; no hay elemento `main`.
- Cambio propuesto: conservar la estructura y usar un landmark principal para facilitar
  navegación asistida. No se detectaron trampas de teclado en los flujos revisados.
- Comando sugerido: `$impeccable polish`.

## Aspectos que se conservan

- Paginación de facturas y búsquedas de catálogos en backend.
- Texto de API insertado con `textContent`; no se interpreta HTML recibido.
- Estados vacíos, errores y descarte de respuestas anteriores en explorers.
- Paneles `details` y diálogos nativos con navegación por teclado y devolución del foco.
- Precio calculado por backend y bloqueo de modificaciones de facturas con CAE.
- TypeScript/Bootstrap sin dependencias de un framework adicional.

## Propuesta de implementación a aprobar

Una pasada acotada sobre la pantalla existente: mantener paneles plegables y explorers;
refinar contraste, espaciado, títulos, controles y presentación numérica; agrupar datos
relacionados de la grilla y mejorar su uso en móvil. Corregir la cantidad inicial,
respetar reducción de movimiento y agregar el landmark principal.

En paralelo, eliminar recorridos redundantes de totales, iniciar cargas independientes y
proteger acciones mientras hay solicitudes. No agregar dashboards, indicadores ficticios,
modo oscuro, cambios de API ni integración ARCA.

La secuencia recomendada es `$impeccable colorize`, `$impeccable adapt`,
`$impeccable harden`, `$impeccable optimize` y, como última pasada, `$impeccable polish`.
Se puede ejecutar el conjunto o priorizar hallazgos puntuales; repetir la auditoría tras
los cambios permite comparar resultados con la misma rúbrica.

## Verificación prevista y límites

- Pruebas de regresión para inicio, operaciones en curso y actualización de totales.
- Comparación del trabajo de render con cantidades crecientes y del arranque con latencia
  controlada; no prometer mejoras porcentuales sin medidas antes/después.
- Navegador: escritorio/móvil, teclado, flujos de alta, búsqueda, cancelación y errores.
- Contraste y dimensiones calculados sobre estilos efectivos, no solo sobre clases.
- No se ejecutó Lighthouse ni se midieron LCP/INP de producción. El viewport móvil no
  sustituye una prueba táctil en dispositivo físico. No se verificaron todos los casos WCAG.

## Resultado de la implementación aprobada

Los hallazgos anteriores describen el estado previo; sus números de línea corresponden
a esa revisión. La pasada aprobada conserva Bootstrap, paneles plegables, explorers,
contratos HTTP y reglas del backend.

- Estilos centralizados en `frontend/public/css/app.css`: superficies, espaciado,
  tipografía numérica, estados, foco y colores consistentes.
- Botón Anular: contraste **6,53:1** sobre blanco (antes 1,63:1). Eliminar: **6,23:1**.
  Valores calculados desde colores efectivos del navegador. Botones e inputs de ítems
  medidos a **44px** (antes 31px).
- Cantidad `1` válida con mínimo/paso `0.01`; selección y alta ficticia con cantidad `2.5`
  y precio 250 produjo total 625, con reinicio correcto del formulario.
- Grilla agrupada en cinco columnas, sin perder los diez datos originales. En móvil se
  presenta como fichas con etiquetas y roles explícitos. A 390px, el listado mide **356px
  visibles / 356px de contenido** (sin desplazamiento lateral), y la página conserva 390px.
  Las tablas de ítems/explorers conservan desplazamiento con indicación y foco de teclado.
- Landmark principal y enlace para saltar al contenido; desplazamiento de mensajes
  inmediato si se solicita reducción de movimiento.
- Acciones bloqueadas durante recargas y por factura durante mutaciones; los handlers
  también impiden duplicados. Se liberan al terminar o fallar, conservando el bloqueo CAE.
- Opciones del alta y listado independientes; estado y reintento locales del alta.
  Borrador inicializado inmediatamente, sin reset tardío al llegar opciones.
- Agregar/quitar ítems conserva los nodos existentes y los valores/foco; las etiquetas
  se reindexan, y quitar la fila enfocada devuelve el foco a una fila vecina.

### Medición de trabajo de renderizado

Mismo harness y mismos ítems sobre copias del JavaScript/HTML anteriores y la versión
integrada. Se cuentan invocaciones, no milisegundos de CPU. La instrumentación vive en
el harness de pruebas y no se incorpora al código de producción.

| Ítems en render completo | Subtotales antes | Subtotales después | Totales globales antes → después |
| ---: | ---: | ---: | ---: |
| 50 | 2.600 | 100 | 51 → 1 |
| 100 | 10.200 | 200 | 101 → 1 |
| 200 | 40.400 | 400 | 201 → 1 |

La parte de cálculo pasa de O(n²) a O(n) en el render completo. Al añadir/quitar una
fila ya no se reconstruyen las demás. Estos conteos no prometen una mejora porcentual
del tiempo total: también intervienen DOM, layout y dispositivo.

### Arranque con latencias simuladas

Reloj controlado: cada catálogo tarda 300ms y la consulta de facturas tarda 200ms.
Las tres consultas pequeñas de catálogo salen juntas en ambas versiones.

| Resultado | Antes | Después |
| --- | ---: | ---: |
| Borrador inicializado | 300ms | 0ms |
| Listado visible | 500ms | 200ms |
| Opciones y listado terminados | 500ms | 300ms |
| Solicitudes iniciales | 4 | 4 |

No son tiempos medidos en producción ni Core Web Vitals. Para repetir la medición actual:
`cd frontend && npm run build && node tests/measure-ui.mjs`. Para comparar una versión
anterior, el script acepta las rutas de su `app.js` compilado y su HTML como argumentos.
En esta sesión se usaron las copias previas guardadas en `/private/tmp/facturacion-app-antes-ui.js`
y `/private/tmp/facturacion-index-antes-ui.html`.

### Verificación final y reevaluación

- `npm test`: **21 pruebas aprobadas**, incluidas 11 de DOM. jsdom se agregó solo como
  dependencia de desarrollo; el navegador no carga una biblioteca adicional.
- Revisión independiente de HTML/CSS y lógica sin hallazgos concretos.
- Detector Impeccable sin hallazgos mecánicos; `git diff --check` sin problemas.
- Navegador con API ficticia: escritorio/móvil, teclado, selección de cliente/precio,
  cantidad decimal, alta, reset y conservación de acciones bloqueadas con CAE.
- El backend no cambió en esta pasada; no se repitió Maven por cambios exclusivos frontend.

Reevaluación orientativa con la misma rúbrica: accesibilidad 3/4, performance 3/4,
responsive 3/4, consistencia visual 3/4 e integridad 4/4: **16/20**. Siguen sin medirse
LCP/INP reales y uso táctil físico; no se afirma conformidad completa WCAG.
