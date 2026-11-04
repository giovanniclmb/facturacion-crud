# Facturación CRUD — Spring Boot + TypeScript

Sistema de facturación de ventas desarrollado como trabajo integrador de Programación III.
Expone una **API REST** con **Spring Boot** para consultar, crear, modificar, anular y eliminar
facturas, genera **reportes en PDF y Excel**, y se consume desde un **frontend web en TypeScript**.

Incluye conexión WSAA/WSFEv1, diagnóstico, autorización de Factura C y recuperación de
resultados inciertos de **ARCA (ex AFIP)** en homologación. ARCA está deshabilitada por
defecto. La emisión requiere declarar un punto de venta de uso exclusivo y una acción
explícita del usuario; no se informa una factura automáticamente al guardarla.

## Stack

| Capa | Tecnología |
| --- | --- |
| Lenguaje backend | Java 21 |
| Framework | Spring Boot 4.1 (Spring Web MVC, Spring Data JPA, Validation) |
| ORM | Hibernate 7 (`jakarta.persistence`) |
| Base de datos | PostgreSQL |
| Boilerplate | Lombok |
| Reportes | OpenPDF 3 (PDF) · Apache POI 5 (Excel `.xlsx`) |
| ARCA | HttpClient de Java · SOAP 1.1 · BouncyCastle 1.86 para firma CMS |
| Frontend | TypeScript 7 · HTML5 · Bootstrap 5.3 · `fetch` con `async`/`await` |
| Build | Maven (backend) · `tsc` + npm (frontend) |

## Arquitectura

```
frontend (localhost:3000)                     backend (localhost:8080)
┌─────────────────────────┐   HTTP + JSON   ┌──────────────────────────────────────────┐
│ index.html              │ ──────────────► │ Controller  (FacturaRestController,      │
│ app.ts      (DOM, UI)   │                 │              CatalogoController)         │
│ FacturaService.ts       │ ◄────────────── │ Service     (FacturaService)             │
│ models/factura.dto.ts   │                 │ Repository  (Spring Data JPA) + JPQL     │
└─────────────────────────┘                 │ Model       (14 entidades JPA + Lombok)  │
                                            └─────────────────────┬────────────────────┘
                                                                  ▼
                                                             PostgreSQL
```

- **DTOs en los dos sentidos:** la API nunca expone entidades. Las consultas proyectan directo a
  `FacturaReporteDto` con `SELECT new` en JPQL, y las altas y modificaciones reciben
  `FacturaRequestDto`, con relaciones por ID.
- **Errores centralizados** con `@RestControllerAdvice`: todas las respuestas de error tienen el
  formato `{ status, mensaje, detalles, fecha }`.
- **Interfaces TypeScript espejo** de los DTOs de Java: mismos nombres de campos y tipos.

## Modelo de dominio

14 entidades que heredan de dos superclases `@MappedSuperclass`: `EntityId` (id autogenerado) y
`AuditoriaApp` (fechas y usuarios de alta, baja y modificación).

`FacturaVenta` → `FacturaVentaDetalle` (composición, `cascade = ALL`, `orphanRemoval = true`) ·
`Cliente` · `PuntoVenta` · `CondicionIva` · `TipoMoneda` · `ListaPrecioArticulo` → `Articulo`,
`ListaPrecio` · `Rubro` · `Marca` · `Contacto` · `Domicilio` · `Usuario`.

## API

| Método | Ruta | Descripción | Respuesta |
| --- | --- | --- | --- |
| GET | `/api/facturas` | Listado paginado con filtros opcionales (ver abajo) | 200 |
| GET | `/api/facturas/{id}` | Una factura | 200 · 404 |
| POST | `/api/facturas` | Alta con ítems | 201 + `Location` · 400 · 404 |
| PUT | `/api/facturas/{id}` | Modificación parcial: cabecera, ítems y/o estado | 200 · 400 · 404 · 409 |
| DELETE | `/api/facturas/{id}` | Borrado físico (sin CAE ni historial fiscal) | 204 · 404 · 409 |
| GET | `/api/facturas/reporte/pdf` | Reporte PDF (mismos filtros) | 200 |
| GET | `/api/facturas/reporte/excel` | Reporte Excel (mismos filtros) | 200 |
| GET | `/api/catalogos/{puntos-venta, clientes, condiciones-iva, monedas, articulos}` | Datos para los formularios | 200 |
| GET | `/api/catalogos/clientes/explorer` | Búsqueda paginada de clientes activos por denominación o CUIT/CUIL | 200 · 400 |
| GET | `/api/catalogos/listas-precio-articulos/explorer` | Búsqueda paginada por código/artículo/lista de precios activos | 200 · 400 |
| GET | `/api/arca/diagnostico?puntoVenta=1` | Estado, ticket y último número autorizado de Factura C en homologación | 200 · 400 · 502 · 503 |
| GET | `/api/facturas/{id}/arca` | Estado fiscal persistido, sin llamadas a ARCA | 200 · 404 |
| POST | `/api/facturas/{id}/arca/autorizar` | Solicitar CAE, JSON `{"confirmarCuitReceptor":true}` | 200 · 202 · 400 · 404 · 409 · 502 · 503 |
| POST | `/api/facturas/{id}/arca/consultar` | Recuperar resultado sin solicitar CAE | 200 · 202 · 404 · 409 · 502 · 503 |

El listado admite `numeroFactura` (coincidencia exacta), `clienteId`, `fechaDesde`,
`fechaHasta` (día completo incluido), `estado`, `montoMinimo` y `sinInformarArca`:
`true` selecciona facturas sin CAE (nulo, vacío o espacios), `false` selecciona facturas
con CAE; omitirlo incluye ambas. Los filtros se combinan con AND.

La paginación se ejecuta en el backend: `pagina` comienza en **0** y `tamanio` vale
**20** por defecto (entre 1 y 100). Por ejemplo:

```http
GET /api/facturas?clienteId=1&sinInformarArca=true&pagina=0&tamanio=20
```

```json
{
  "contenido": [],
  "pagina": 0,
  "tamanio": 20,
  "totalElementos": 0,
  "totalPaginas": 0
}
```

**Cambio de contrato:** `GET /api/facturas` devuelve este objeto en lugar del array anterior.
El frontend incluido ya consume `contenido`. El orden es fecha de emisión, número e ID;
una página fuera del total devuelve contenido vacío conservando el total.
Los parámetros inválidos y los rangos de fechas invertidos responden 400.

PDF y Excel aceptan los mismos filtros y exportan **todos** los resultados, sin paginar.
En la pantalla, Buscar aplica los filtros y vuelve a la primera página; navegar y exportar
conserva esa búsqueda aunque se estén editando nuevos filtros. El importe del resumen
corresponde únicamente a la página visible.

### Explorers de clientes y precios

Los botones **Buscar** de Cliente (en filtros y alta) y **Buscar artículo** de cada ítem
abren ventanas con búsqueda, grilla y selección. Usan consultas paginadas de 10 resultados;
no descargan todos los clientes y precios al iniciar. Tab permite recorrer los controles,
Enter busca o activa la selección y Escape/Cancelar cierra sin reemplazar el registro elegido.

Ambos endpoints explorer aceptan `busqueda`, `pagina` y `tamanio`, y devuelven `PaginaDto`
con la misma estructura del listado. El backend usa tamaño 20 por defecto y máximo 100.
La búsqueda ignora mayúsculas/minúsculas y trata `%`, `_` y `!` como texto literal.
El explorer de precios muestra todas las relaciones activas entre artículo y lista, con su
precio; excluye registros si la relación, el artículo o la lista están dados de baja.

Cliente devuelve `id`, `denominacion`, `cuitCuil`. ListaPrecioArticulo devuelve `id`,
`articuloId`, `articuloCodigo`, `articuloDenominacion`, `listaPrecioCodigo`,
`listaPrecioDenominacion`, `precioVenta`.

Al seleccionar un precio, el detalle envía `articuloId` y `listaPrecioArticuloId`.
El backend comprueba que correspondan y estén activos, y toma el **precio actual de la
base de datos**. Si ese precio cambió desde la búsqueda, el importe final puede diferir
del estimado. No se envía un precio editable desde el navegador.
Omitir `listaPrecioArticuloId` sigue permitido: el backend selecciona el último precio
cuyo registro, artículo y lista estén activos. La revisión del 03/11/2026 endureció este
fallback: antes podía elegir un precio de una lista dada de baja. Un consumidor que
omitía el ID puede obtener otro precio vigente o un 404 si no queda ninguno; los
endpoints anteriores de catálogos se mantienen.

### Secciones plegables del INDEX

Búsqueda, Nueva factura y Comprobantes registrados se expanden o contraen por separado
al activar sus encabezados con clic, Enter o Espacio. Al iniciar, búsqueda y listado están
abiertos y Nueva factura está cerrada. Plegar un panel conserva filtros, borrador, ítems y
resultados durante la sesión de página; el resumen del listado sigue visible al cerrarlo.
Se usan `details` y `summary` nativos, sin nuevas dependencias ni JavaScript adicional.

### Diseño, carga y uso en móvil

La grilla agrupa comprobante/fecha/punto de venta, cliente/IVA, importe/ítems y estado/CAE,
conservando todos los datos. En móvil se presenta como fichas con etiquetas y acciones
visibles. Los controles tienen al menos 44px de alto, foco visible y colores con contraste
revisado; los estilos están centralizados en `frontend/public/css/app.css`.

El listado y las opciones del alta se cargan en paralelo. Si falla la carga de opciones,
se puede reintentar sin perder el borrador ni bloquear la consulta de comprobantes.
Las acciones de fila se deshabilitan durante recargas y operaciones en curso; se evita
enviar dos operaciones simultáneas sobre la misma factura. Añadir y quitar ítems conserva
los campos existentes y su foco, y calcula el total una sola vez por operación.

La [revisión de UI y performance](docs/revisiones/2026-10-30-ui-performance.md) incluye
hallazgos iniciales, correcciones y mediciones comparables con latencia simulada.

**Ejemplo de alta:**

```json
POST /api/facturas
{
  "puntoVentaId": 1,
  "clienteId": 1,
  "condicionIvaId": 1,
  "tipoMonedaId": 1,
  "observaciones": "Venta mostrador",
  "detalles": [
    { "articuloId": 1, "cantidad": 2 },
    { "articuloId": 2, "cantidad": 1, "porcentajeBonificacion": 10 }
  ]
}
```

## Reglas de negocio

- **Factura C** (emisor monotributista): no discrimina IVA; `importeNeto = importeSubtotal` e `importeIva = 0`.
- **El backend calcula todos los importes.** El precio sale del registro activo de
  ListaPrecioArticulo elegido; el navegador envía sus IDs y la cantidad. Sin selección
  explícita, la API mantiene la regla anterior del último precio con relación, artículo y lista activos.
- **Ciclo de estados:** toda factura nace `PENDIENTE` y pasa a `EMITIDA` al autorizarse
  en ARCA. Solo las facturas sin CAE pueden anularse (con `fechaAnulacion`).
- **Con CAE** la factura queda en solo lectura: no puede modificarse, anularse ni eliminarse.
  Los intentos de PUT o DELETE válidos responden **409** sin alterar cabecera, ítems ni
  auditoría; el bloqueo también abarca observaciones y cambios de estado.
- **Sin CAE** permite modificación/anulación si no hay intento fiscal activo; una vez
  anulada no admite más modificaciones. Un intento `PREPARANDO`, `ENVIANDO` o `INCIERTO`
  bloquea las mutaciones. Un rechazo confirmado permite corregir/anular; el historial
  fiscal impide el borrado físico. La UI aplica los permisos devueltos por el backend.
- **Numeración fiscal:** el número local se conserva; el número fiscal se asigna dentro
  de una reserva durable por emisor/punto/tipo. Ante respuesta perdida se consulta y
  compara el snapshot antes de adoptar un CAE; no se reenvía automáticamente.
- **Transacciones:** el alta y la modificación son atómicas (`@Transactional`); si falla un ítem, no se guarda nada.

La aplicación es académica y local, sin autenticación de usuarios. El servidor escucha
por defecto en `127.0.0.1`; no está preparada para exposición pública sin incorporar
control de acceso. La publicación del código en GitHub no despliega la API.

## Cómo ejecutarlo

**Requisitos:** JDK 21+, PostgreSQL y Node.js compatible con las dependencias de desarrollo.
Para las pruebas DOM con jsdom: Node 22.22.2+ en la rama 22, 24.15.0+ en la rama 24 o
26+. El entorno verificado utiliza Node 24.19.0.

### 1. Base de datos

Crear la base `facturacion_crud` con los datos maestros cargados (clientes, artículos, listas de
precios, puntos de venta, etc.). El proyecto se probó con una copia de la base del TP
[jpa-facturacion](https://github.com/giovanniclmb/jpa-facturacion):

```bash
createdb -T facturacion facturacion_crud
```

Ajustar usuario y clave en `src/main/resources/application.properties`. El arranque usa
`ddl-auto=validate`: la base debe contener el esquema heredado y la
[migración ARCA](docs/arca/MIGRACION_AUTORIZACION.md). No usar `ddl-auto=update` para
crear las restricciones de intentos fiscales; las define la migración explícita.

### 2. Backend

```bash
./mvnw spring-boot:run
```

API disponible en `http://localhost:8080/api/facturas`.

### 3. Frontend

```bash
cd frontend
npm install
npm run dev
```

Abrir `http://localhost:3000`. `npm run dev` recompila TypeScript ante cada cambio y sirve la carpeta `public/`.

## Estructura

```
facturacion-crud/
├── pom.xml
├── src/main/java/com/tp/facturacion/
│   ├── config/        CORS
│   ├── controller/    endpoints REST
│   ├── dto/           DTOs de entrada, salida, filtros y catálogos
│   ├── exception/     excepciones propias y @RestControllerAdvice
│   ├── model/         entidades JPA con Lombok
│   ├── reporte/       generadores de PDF y Excel
│   ├── repository/    repositorios Spring Data JPA
│   └── service/       lógica de negocio
└── frontend/
    ├── public/index.html
    ├── public/css/app.css          estilos y presentación adaptable
    ├── tests/                     pruebas HTTP, DOM y mediciones controladas
    └── src/
        ├── app.ts                  manejo del DOM y eventos
        ├── models/factura.dto.ts   interfaces espejo de los DTOs
        └── services/               cliente HTTP (FacturaService, CatalogoService)
```

## Próximos pasos

- Acordar una factura de prueba y un punto exclusivo para comprobar el primer CAE real
  de homologación. Certificado, autorización WSASS y diagnóstico real ya están verificados.
- Ampliaciones futuras: otros tipos de documento, servicios y moneda extranjera. Producción
  sigue fuera del alcance implementado.

## Conexión ARCA en homologación

La [guía de configuración y diagnóstico](docs/arca/HOMOLOGACION.md) explica cómo reutilizar
el CSR existente, gestionar WSASS y completar `arca-local.properties` a partir del ejemplo
sin datos privados. El proyecto funciona sin credenciales con `arca.habilitado=false`.

Para emitir: aplicar la [migración aditiva](docs/arca/MIGRACION_AUTORIZACION.md), configurar
`arca.puntos-venta-exclusivos` y seguir la [guía de autorización](docs/arca/HOMOLOGACION.md).
La base local ya fue migrada; el arranque usa `ddl-auto=validate` y no crea restricciones
fiscales implícitamente. No declarar un punto exclusivo si también lo usa otro sistema.

El diagnóstico **no emite CAE ni modifica facturas**. Consulta el estado del servicio,
obtiene/reutiliza un ticket privado, consulta puntos de venta y el último número autorizado;
advierte cuando el catálogo devuelve 602 sin acreditar habilitación para emitir. Los destinos están restringidos a homologación y el ticket no
se devuelve por HTTP ni se registra en logs.

La implementación está verificada con certificados efímeros y SOAP simulado. También
se verificó el diagnóstico autenticado real en homologación; ver la guía para la advertencia
del catálogo de puntos de venta vacío (602). No copiar al repositorio claves,
certificados, CSR, tickets ni datos privados del ejemplo de la cátedra.

## Verificación

```bash
# Pruebas de búsqueda y reportes, aisladas en H2 en memoria
./mvnw -Dtest=FacturaBusquedaTests test

# Explorers y selección explícita de precios, también en H2
./mvnw -Dtest=CatalogoExplorerTests test

# Inmutabilidad con CAE y regresiones de facturas sin CAE, en H2
./mvnw -Dtest=FacturaCaeInmutableTests test

# Cliente ARCA y diagnóstico: pruebas sin credenciales reales
./mvnw '-Dtest=**/arca/*Tests' test

# Suite completa; valida el esquema existente sin actualizarlo en contextLoads
./mvnw test -Dspring.jpa.hibernate.ddl-auto=validate

# Compilación TypeScript y pruebas del cliente HTTP, estado y DOM
cd frontend
npm test

# Medición controlada de arranque y cálculos de la versión actual
node tests/measure-ui.mjs
```

Las pruebas de búsqueda ejercitan el controlador, las consultas JPQL, altas con distintos
precios y las exportaciones reales PDF/XLSX con datos ficticios en H2. `contextLoads` sigue
requiriendo PostgreSQL local. Las pruebas frontend incluyen cliente HTTP, descarte de
respuestas anteriores, arranque, reintentos, conservación del borrador/foco y bloqueo de
acciones simultáneas. jsdom es una dependencia de desarrollo para estas pruebas, sin
añadir bibliotecas al navegador. Las mediciones de `measure-ui.mjs` son deterministas
y no sustituyen Lighthouse ni métricas de producción.

## Autor

Giovanni — [github.com/giovanniclmb](https://github.com/giovanniclmb)


Verificación de autorización (03/11/2026): 218 pruebas backend y 35 frontend aprobadas;
incluye PostgreSQL real en esquemas aislados y circuito completo con persistencia y ARCA
simulada. Migración local aplicada, datos previos conservados. Primera emisión real de
CAE de homologación pendiente de acordar factura y punto exclusivo.
