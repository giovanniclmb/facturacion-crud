# Facturación CRUD — Spring Boot + TypeScript

Sistema de facturación de ventas desarrollado como trabajo integrador de Programación III.
Expone una **API REST** con **Spring Boot** para consultar, crear, modificar, anular y eliminar
facturas, genera **reportes en PDF y Excel**, y se consume desde un **frontend web en TypeScript**.

La integración con **ARCA (ex AFIP)** para obtener el CAE en homologación está planificada (ver *Próximos pasos*).

## Stack

| Capa | Tecnología |
| --- | --- |
| Lenguaje backend | Java 21 |
| Framework | Spring Boot 4.1 (Spring Web MVC, Spring Data JPA, Validation) |
| ORM | Hibernate 7 (`jakarta.persistence`) |
| Base de datos | PostgreSQL |
| Boilerplate | Lombok |
| Reportes | OpenPDF 3 (PDF) · Apache POI 5 (Excel `.xlsx`) |
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
| GET | `/api/facturas` | Listado con filtros opcionales `fechaDesde`, `fechaHasta`, `estado`, `montoMinimo` | 200 |
| GET | `/api/facturas/{id}` | Una factura | 200 · 404 |
| POST | `/api/facturas` | Alta con ítems | 201 + `Location` · 400 · 404 |
| PUT | `/api/facturas/{id}` | Modificación parcial: cabecera, ítems y/o estado | 200 · 400 · 404 · 409 |
| DELETE | `/api/facturas/{id}` | Borrado físico (solo sin CAE) | 204 · 404 · 409 |
| GET | `/api/facturas/reporte/pdf` | Reporte PDF (mismos filtros) | 200 |
| GET | `/api/facturas/reporte/excel` | Reporte Excel (mismos filtros) | 200 |
| GET | `/api/catalogos/{puntos-venta, clientes, condiciones-iva, monedas, articulos}` | Datos para los formularios | 200 |

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
- **El backend calcula todos los importes.** El precio de cada ítem sale de la lista de precios
  vigente del artículo; el cliente de la API solo envía `articuloId` y `cantidad`.
- **Ciclo de estados:** toda factura nace `PENDIENTE`, pasa a `EMITIDA` al autorizarse en ARCA, y
  puede pasar a `ANULADA` (con `fechaAnulacion`).
- **Con CAE** la factura no puede modificarse ni eliminarse; solo anularse.
- **Sin CAE** puede modificarse y eliminarse físicamente.
- **Transacciones:** el alta y la modificación son atómicas (`@Transactional`); si falla un ítem, no se guarda nada.

## Cómo ejecutarlo

**Requisitos:** JDK 21+, PostgreSQL, Node.js 18+.

### 1. Base de datos

Crear la base `facturacion_crud` con los datos maestros cargados (clientes, artículos, listas de
precios, puntos de venta, etc.). El proyecto se probó con una copia de la base del TP
[jpa-facturacion](https://github.com/giovanniclmb/jpa-facturacion):

```bash
createdb -T facturacion facturacion_crud
```

Ajustar usuario y clave en `src/main/resources/application.properties`. Las tablas se crean o
actualizan solas (`ddl-auto=update`).

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
    └── src/
        ├── app.ts                  manejo del DOM y eventos
        ├── models/factura.dto.ts   interfaces espejo de los DTOs
        └── services/               cliente HTTP (FacturaService, CatalogoService)
```

## Próximos pasos

- Autorización de comprobantes en **ARCA (ex AFIP)**, entorno de homologación: autenticación
  con certificado (WSAA) y solicitud de CAE (WSFEv1). Las facturas autorizadas pasarán de
  `PENDIENTE` a `EMITIDA` con su CAE.

## Autor

Giovanni — [github.com/giovanniclmb](https://github.com/giovanniclmb)