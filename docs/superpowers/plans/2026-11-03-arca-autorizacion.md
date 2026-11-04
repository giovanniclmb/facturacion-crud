# Autorización recuperable ARCA — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Autorizar Factura C en homologación y recuperar respuestas perdidas con numeración fiscal y trazabilidad durables.

**Architecture:** Snapshot fiscal inmutable, reserva por serie en PostgreSQL, transacciones cortas y clientes SOAP inyectables. El orquestador no es transaccional; el almacenamiento transaccional es otro bean. El frontend presenta los estados y permisos que determina el backend.

**Tech Stack:** Java 21, Spring Boot/JPA actuales, PostgreSQL, H2 para pruebas funcionales, JUnit/Mockito, TypeScript/jsdom. Reutilizar SOAP/XML/WSAA y BouncyCastle existentes. Sin cola, scheduler, Flyway ni dependencias nuevas.

**Spec:** [Diseño aprobado](../specs/2026-11-03-arca-autorizacion-design.md), aprobado por el usuario el 03/11/2026.

## Global Constraints

- Solo HOMOLOGACION, Factura C (11), Concepto 1, PES y cotización 1.
- DocTipo 80: CUIT receptor válido y distinto del emisor; no inferir CUIT desde CUIL.
- Número local conservado, número fiscal separado. Un comprobante por solicitud.
- Sin IVA ni tributos en este corte: ImpIVA/ImpTrib/ImpTotConc/ImpOpEx=0, ImpNeto=ImpTotal.
- Sin autenticación o emisión durante arranque ni al guardar borradores.
- Intento durable antes del envío; no mantener locks/transacciones durante red.
- Nunca liberar INCIERTO por edad, ni reenviar automáticamente, ni adoptar CAE por número solamente.
- No leer/imprimir secretos para explorar. Pruebas automatizadas con credenciales sintéticas.
- Conservar cambios previos sin staging global ni commits de trabajo ajeno.
- Archivos compartidos se editan secuencialmente. Ejecución con equipo AGENTS.md ya elegido.

## Review Focus

1. Primera reserva simultánea de una serie inexistente: una reserva gana; otra observa existente/409, sin transacción abortada reutilizada. Prueba tarea 3.
2. Proceso tardío tras recuperar PREPARANDO: propietario viejo no puede pasar a ENVIANDO; un timeout sin respuesta no habilita edición. Prueba tarea 4.
3. Cambio de factura entre preflight y reserva: validar snapshot fresco bajo lock; no enviar datos anteriores mezclados. Prueba tareas 2/3.
4. Número ocupado externamente con otro receptor/importe: consultar y mantener conflicto, sin asociar CAE ajeno. Prueba tarea 4.
5. Rechazo seguido de corrección y nuevo intento usando el mismo número: conservar ambos snapshots y permitir reutilización solo tras rechazo confirmado. Prueba tareas 3/4.

## Responsables, archivos e interfaces

Orchestrator/Integrator coordina y aplica migración después de verificarla. Worker Sol medium implementa tareas 1–5; Worker frontend Sol medium puede implementar tarea 6 solo tras fijar contratos de tarea 5. Reviewer Luna medium revisa persistencia, concurrencia y SOAP antes de integración. Explorer Terra se usa solo si surge una incertidumbre de rutas; no repetir exploración ya realizada. No más de cuatro agentes; ningún agente crea otros sin coordinación.

| Unidad | Archivos nuevos o existentes |
|---|---|
| Tipos fiscales y snapshot | `arca/autorizacion/EstadoIntentoArca.java`, `ContratoFiscal.java`, `SnapshotFiscalFactory.java` |
| SOAP de autorización | `arca/autorizacion/WsfeAutorizacionClient.java` (reutiliza `WsfeClient` para lecturas existentes) |
| Persistencia | `arca/autorizacion/IntentoArca.java`, `SerieArca.java`, `IntentoArcaRepository.java`, `SerieArcaRepository.java`, `AlmacenIntentosArca.java` |
| Orquestación | `arca/autorizacion/AutorizacionArcaService.java` |
| HTTP | `controller/FacturaArcaController.java`, `dto/FacturaArcaDto.java`, `dto/AutorizarArcaRequest.java` |
| Facturas existentes | `model/FacturaVenta.java`, `repository/FacturaVentaRepository.java`, `service/FacturaService.java`, `dto/FacturaReporteDto.java`, `exception/GlobalExceptionHandler.java` |
| Migración | `db/migrations/001_arca_autorizacion.sql`, `docs/arca/MIGRACION_AUTORIZACION.md` |
| Cliente | `frontend/src/models/factura.dto.ts`, `models/arca.dto.ts`, `models/permisosFactura.ts`, `services/FacturaService.ts`, `components/AutorizacionArca.ts`, `app.ts`, `public/css/app.css` |
| Reportes | `reporte/FacturaPdfExporter.java`, `FacturaExcelExporter.java` |

Todas las rutas Java son relativas a `src/main/java/com/tp/facturacion/`. Las pruebas nuevas se ubican en `src/test/java/com/tp/facturacion/arca/autorizacion/`.

### Contratos comunes que fijan las dependencias

`ContratoFiscal` agrupa records y tipos cerrados, sin secretos ni entidades JPA:

```java
public record Serie(String ambiente, String emisor, int puntoVenta, int tipo) {}
public record Snapshot(int concepto, int docTipo, long docNumero, int condicionIva,
        LocalDate fecha, String moneda, BigDecimal cotizacion, BigDecimal total) {}
public record ErrorFiscal(int codigo, String mensaje) {}
public record ResultadoEnvio(boolean aceptado, String cae, LocalDate vencimiento,
        List<ErrorFiscal> errores, List<ErrorFiscal> observaciones) {}
public record Comprobante(Serie serie, long numero, Snapshot snapshot,
        String cae, LocalDate vencimiento) {}
public record Consulta(Optional<Comprobante> comprobante) {}
public record Reserva(UUID intento, UUID propietario, boolean creada) {}
public record Envio(UUID intento, Serie serie, long numero, Snapshot snapshot) {}
```

Imports: `java.time.LocalDate`, `java.math.BigDecimal`, `java.util.*`. Estos son tipos públicos anidados en `ContratoFiscal`, importados estáticamente donde corresponda. `Consulta(Optional.empty())` significa exclusivamente 602 válido de FECompConsultar, nunca fallo de transporte. El snapshot se amplía con campos cero explícitos si el parser necesita contrastarlos: todos deben quedar en cero y no se toleran importes omitidos.

```java
// EstadoIntentoArca
PREPARANDO, ENVIANDO, INCIERTO, AUTORIZADO, RECHAZADO, FALLIDO_ANTES_ENVIO

// WsfeAutorizacionClient (cada operación recibe el ticket explícitamente)
ResultadoEnvio autorizar(TicketAcceso ticket, Envio envio);
Consulta consultar(TicketAcceso ticket, Serie serie, long numero);

// SnapshotFiscalFactory: sin red; Clock inyectado en zona Argentina para fecha fiscal
Snapshot crear(FacturaVenta factura, String emisor, Set<Integer> ivaClaseC,
               boolean confirmarCuitReceptor);
String hash(Snapshot snapshot); // orden fijo, UTF-8, decimales canónicos, SHA-256

// AlmacenIntentosArca: bean separado, transacciones cortas
Reserva reservar(long facturaId, Serie serie, Set<Integer> ivaClaseC, boolean confirmarCuit);
Optional<Envio> comenzarEnvio(Reserva reserva, long numero); // CAS propietario/estado
void finalizar(UUID intento, ResultadoEnvio resultado);
void marcarIncierto(UUID intento);
void fallarAntesEnvio(Reserva reserva);
FacturaArcaDto estado(long facturaId);
Optional<Envio> envioPendiente(long facturaId);
void recuperar(UUID intento, Comprobante comprobante); // compara todos los datos
Reserva recuperarPreparando(long facturaId); // cambia propietario bajo lock

// AutorizacionArcaService: nunca @Transactional
FacturaArcaDto autorizar(long facturaId, boolean confirmarCuit);
FacturaArcaDto consultar(long facturaId);
FacturaArcaDto estado(long facturaId);
```

El método reservar devuelve `creada=false` para un intento activo/aceptado de la misma factura y no permite despacharlo desde esa solicitud. Si la misma factura tiene rechazo confirmado crea otro intento. Otra factura ocupando serie recibe ReglaNegocioException (409). `recuperarPreparando` solo aplica antes de ENVIANDO; invalida al dueño previo mediante un nuevo UUID. La adquisición inicial de serie usa INSERT ON CONFLICT DO NOTHING y SELECT FOR UPDATE en PostgreSQL, no un catch que continúe tras violar unicidad.

## Tarea 1: snapshot validado y contrato SOAP de emisión/consulta

**Archivos:** crear `EstadoIntentoArca.java`, `ContratoFiscal.java`, `SnapshotFiscalFactory.java`, `WsfeAutorizacionClient.java`; pruebas `SnapshotFiscalTests.java`, `WsfeAutorizacionClientTests.java`; fixtures `src/test/resources/arca/autorizacion/`.

**Consume:** `ArcaProperties`, `SoapHttpClient`, `XmlSeguro`, `TicketAcceso`, reloj existente. **Produce:** records, factory y dos métodos SOAP con firmas anteriores.

- [x] Leer WSDL público guardado y manual oficial anterior/vigente. La página oficial etiqueta v4.7 pero el PDF compartido presenta portada futura v4.8: guardar versión/huella/fecha del contrato consultado y no adoptar reglas futuras por etiqueta del enlace. Verificar antes de fijar validaciones: Concepto1 permite ventana de cinco días y restricción de mes, moneda PES/cot1, campos de receptor y CondicionIVAReceptorId.
- [x] Escribir y ejecutar prueba roja de validación (adaptar fixture con CUIT sintético de dígito válido distinto del emisor):

```java
@Test void noConvierteCuilEnCuitSinConfirmacion() {
    assertThrows(IllegalArgumentException.class,
        () -> factory.crear(facturaValida(), EMISOR_FICTICIO, Set.of(5), false));
}
@Test void consultaNoEncontradaNoEquivaleAFalloDeRed() {
    assertTrue(clienteConRespuesta602().consultar(ticket, serie, 1).comprobante().isEmpty());
    assertThrows(ArcaRemotaException.class,
        () -> clienteConFault().consultar(ticket, serie, 1));
}
```

- [x] Crear helpers de fixture en el propio test; no usar datos reales. Añadir tests para CUIT checksum/emisor, NaN/infinito/negativo/cero, moneda distinta, IVA fuera de catálogo C, fecha inválida y detalle/total incoherentes. Convertir `BigDecimal.valueOf(double)` solo tras finitud, redondear HALF_UP a dos decimales y comparar suma de subtotales normalizados con total. No recalcular precios de listas actuales: usar valores guardados de la factura.
- [x] Implementar snapshot y hash sin red. Confirmación explícita de CUIT requerida en la acción de autorización porque Cliente solo tiene cuitCuil; no inferir por prefijo ni permitir que el navegador cambie el documento.
- [x] Escribir tests rojos para FECAESolicitar: cabecera CantReg=1/PtoVta/CbteTipo11; detalle Concepto1/DocTipo80/DocNro/CbteDesde=CbteHasta/fecha/importes/PES/cot1/IVA receptor; aceptar A solo con cabecera y detalle coherentes, CAE numérico de 14 dígitos y vencimiento válido. Respuesta R explícita y coherente es rechazo; cabecera/resultado ausente, múltiples detalles, A sin CAE o combinación contradictoria es ArcaRemotaException. Observaciones A no se tratan como R.
- [x] Implementar XML con escape y namespace existentes; secuencia de importes ImpTotConc, ImpNeto, ImpOpEx, ImpTrib, ImpIVA según WSDL. No array IVA para C. `FECompConsultar`: parsear todos los campos obligatorios, CodAutorizacion/FchVto y resultado; solo Err602 único de esa operación produce Optional.empty; 602 mezclado, Fault o XML incoherente sigue fallando.
- [x] Ejecutar `./mvnw -Dtest=SnapshotFiscalTests,WsfeAutorizacionClientTests test`. Observar rojo y luego verde. Revisar que ni excepciones ni toString conserven XML autenticado.

## Tarea 2: migración aditiva y entidades de intentos/series

**Archivos:** migración y guía; `pom.xml` para perfil de pruebas PostgreSQL sin dependencias nuevas; entidades/repositorios de tabla anterior; ampliar `FacturaVenta`; tests `MigracionArcaPostgresTests.java` y `PersistenciaArcaTests.java`; fixture SQL de esquema heredado en `src/test/resources/arca/autorizacion/base-heredada.sql`.

**Consume:** tipos tarea 1. **Produce:** entidades y esquema con restricciones verificadas.

- [x] Agregar perfil Maven `arca-postgres` que establece la propiedad de sistema `arca.postgres=true` en Surefire. Los tests PostgreSQL usan `@EnabledIfSystemProperty(named="arca.postgres", matches="true")`; URL ausente con ese perfil falla en setup, no omite pruebas.
- [x] Escribir test rojo sobre PostgreSQL real con `ARCA_TEST_JDBC_URL`, usuario/password por `ARCA_TEST_JDBC_USER` y `ARCA_TEST_JDBC_PASSWORD` y esquema aleatorio `arca_test_<uuid>`; no usar esquema public ni datos del usuario. Al habilitar perfil `arca-postgres`, la falta de URL debe fallar claramente. Deshabilitado en suite normal con condición explícita y reportado, nunca presentado como aprobado. Copiar DDL de entidades heredadas a fixture sin datos privados; usar el esquema temporal en search_path.
- [x] Crear migración SQL transaccional de ejecución explícita, one-shot, sin DROP ni UPDATE de datos previos. No usar IF NOT EXISTS para ocultar estructuras incompatibles. Transacción fallida/rerun revierte sin alterar esquema. Conservar número local y agregar a factura campos opcionales `numero_fiscal bigint`, `punto_venta_fiscal integer`, `tipo_comprobante_fiscal integer`, `ambiente_fiscal text`, `emisor_fiscal text`.
- [x] Crear `arca_serie` con id bigint y unique(ambiente,emisor,punto_venta,tipo), propietario/reserva por intento UUID; crear `arca_intento` con UUID PK, factura FK restrict, serie FK, propietario UUID, estado check, numero bigint nullable, snapshot embebido por columnas exactas (fecha date, importes numeric(18,2), cotizacion numeric(18,6), receptor bigint, códigos), hash text, created/updated timestamptz, CAE/vencimiento, errores/observaciones estructurados saneados y versión bigint. Completar FK de reserva sin cascada después de crear ambas tablas.
- [x] Crear índice de búsqueda factura/fecha/id y restricciones parciales concretas:

```sql
CREATE UNIQUE INDEX uq_arca_factura_activa
ON arca_intento(factura_id)
WHERE estado IN ('PREPARANDO','ENVIANDO','INCIERTO','AUTORIZADO');
CREATE UNIQUE INDEX uq_arca_numero_reservado
ON arca_intento(serie_id, numero)
WHERE numero IS NOT NULL AND estado IN ('PREPARANDO','ENVIANDO','INCIERTO','AUTORIZADO');
```

- [x] Mapear JPA con snapshot sin setters de negocio, PK UUID generada en aplicación, enum STRING, @Version, Instant y LocalDate; toString excluye información fiscal sensible. No heredar AuditoriaApp para estas tablas técnicas; registrar usuario iniciador por FK si hay auditoría, usando usuarioSistema actual.
- [x] Pruebas: fixtures viejos conservados, constraints de dos activos/un número aceptado, dos rechazos mismo número permitidos, FK impide borrar historial y migración repetida revierte limpiamente. Ejecutar además validate JPA sobre el esquema migrado.
- [x] Guía con preflight `psql --dbname=facturacion_crud --username=giovita -c '\d factura_venta'`, copia de seguridad privada fuera de Git, comando `psql --dbname=facturacion_crud --username=giovita -v ON_ERROR_STOP=1 -f db/migrations/001_arca_autorizacion.sql`, validación posterior y arranque con ddl-auto=validate. No aplicar aún a la base real. Recuperación operativa: volver a código previo no elimina tablas/intentos; deshabilitar nuevas autorizaciones y reconciliar pendientes conservando historia.

## Tarea 3: reserva atómica y bloqueo de mutaciones locales

**Archivos:** `AlmacenIntentosArca.java`, repositorios nuevos, `FacturaVentaRepository.java`, `FacturaService.java`; tests `ReservaArcaTests.java`, `ReservaArcaPostgresTests.java`, ampliar `FacturaCaeInmutableTests.java`.

**Consume:** factory, entidades, repositorios. **Produce:** métodos transaccionales del contrato común; bloquear factura mediante consulta PESSIMISTIC_WRITE de raíz (sin outer join fetch).

- [x] Escribir y ejecutar prueba roja de doble reserva de la primera serie, con dos conexiones/transacciones reales y CountDownLatch (sin sleep como coordinación):

```java
@Test void primeraSerieTieneUnSoloGanador() throws Exception {
    var resultados = reservarEnParalelo(factura1, factura2, serieNueva);
    assertEquals(1, resultados.creadas());
    assertEquals(1, resultados.conflictos());
    assertEquals(1, contarActivos(serieNueva));
}
```

- [x] Implementar INSERT ON CONFLICT DO NOTHING + lock de serie y luego factura en transacción corta. `lock_timeout` local acotado a 3 segundos para PostgreSQL. En H2 usar repositorio equivalente para pruebas, sin afirmar que prueba concurrencia PostgreSQL. Revalidar estado, CAE y snapshot fresco dentro del lock, con códigos de catálogo obtenidos antes de reservar. Cambios detectados se rechazan/revalidan, no producen un snapshot combinado.
- [x] Devolver mismo intento activo/aceptado si factura coincide, 409 si otra ocupa la serie. Reservar nueva tentativa solo después de rechazo/fallo-antes-envío. No mantener conexión transaccional durante red.
- [x] Implementar comenzarEnvio: comprobar propietario y PREPARANDO, numero positivo, cambiar a ENVIANDO con commit y devolver Envio solo al ganador. `fallarAntesEnvio` solo puede cerrar PREPARANDO y dueño coincidente; nunca cierra ENVIANDO.
- [x] `FacturaService.actualizar/eliminar`: tomar lock de factura primero y verificar CAE/activos. No adquirir después lock de serie desde esas rutas (evitar inversión). Factura con historial no se borra físicamente incluso tras rechazo; sí permite corregir/anular sin borrar historia. Evitar dependencia circular: repositorios/guardia simple, no AutorizacionArcaService dentro de FacturaService.
- [x] Añadir pruebas de carrera actualización-reserva (o una gana o se usa snapshot final), borrado rechazado con historial, rechazo libera edición pero conserva snapshot, número rechazado reutilizable, series distintas independientes. Ejecutar tests H2 y perfil PostgreSQL explícito antes de aprobar tarea.

## Tarea 4: envío único y recuperación tras fallos

**Archivos:** `AutorizacionArcaService.java`, completar `AlmacenIntentosArca.java`; tests `AutorizacionArcaServiceTests.java`, `RecuperacionArcaTests.java`.

**Consume:** contratos tareas 1–3, WsaaClient, WsfeClient lecturas. **Produce:** autorizar/consultar/estado, sin @Transactional de orquestación.

- [x] Prueba roja mínima de despacho único y recuperación:

```java
@Test void aceptadaConFalloDeGuardadoSeRecuperaSinReenviar() {
    simularAceptacionYFalloPersistencia();
    servicio.autorizar(ID, true);
    servicio.consultar(ID);
    verify(cliente, times(1)).autorizar(any(), any());
    assertEquals(AUTORIZADO, almacen.estado(ID).estadoFiscal());
}
@Test void consultaNoEncontradaMantieneBloqueo() {
    prepararIntentoIncierto();
    simularConsultaSinRegistro();
    assertEquals(INCIERTO, servicio.consultar(ID).estadoFiscal());
    assertFalse(almacen.estado(ID).puedeModificar());
}
```

- [x] Flujo: props válidas y catálogo IVA C → reservar → obtener ticket/último autorizado sin tx → comenzarEnvio CAS → FECAESolicitar → finalizar. Si reserva no es nueva devolver estado, nunca despachar. Errores previos a comenzarEnvio: cerrar únicamente PREPARANDO del propio dueño; errores posteriores conservar bloqueo. Fallo al guardar incertidumbre conserva ENVIANDO durable y se presenta 202 consultable.
- [x] `finalizar`: bajo locks serie/factura, validar intento actual y aceptar solo respuesta coherente; persistir resultado y campos factura en una transacción, luego liberar referencia activa. Un R explícito guarda rechazo y libera; rechazo no es HTTP502. No cambiar el estado comercial de cobro automáticamente.
- [x] Consultar ENVIANDO/INCIERTO por FECompConsultar; coincidencia usa equals lógico de campos y BigDecimal.compareTo, no formato textual ni solo hash parcial. ImpIVA/Trib/exentos/no gravados deben concordar con cero. CAE válido/vencimiento y tipo/punto/número deben coincidir. Reconciliación repetida es idempotente, no sobrescribe AUTORIZADO con una respuesta tardía R.
- [x] Si otro emisor/ambiente configurado, no usar su ticket para recuperar intento anterior: 409/503 saneado, mantener intento original. Un número ocupado por otra factura queda INCIERTO con motivo de conflicto y sin CAE asociado.
- [x] Recuperar PREPARANDO sin número: nueva propiedad CAS bajo lock, consultar último y continuar preparación; dueño antiguo no puede iniciar. No transferir ni reenviar ENVIANDO, aunque sea antiguo. Si no encontrado, queda INCIERTO y solo se permite otra consulta; no incluir reenvío manual en este corte.
- [x] Probar fallo antes de red, fallo tras commit ENVIANDO antes de enviar, timeout, reinicio/otra instancia, colisión, respuesta tardía, misma factura ya aceptada y nuevo intento tras rechazo con idéntico número. Ejecutar `./mvnw -Dtest=AutorizacionArcaServiceTests,RecuperacionArcaTests test`.

## Tarea 5: endpoints, estado fiscal en grilla y reportes

**Archivos:** controlador/DTOs nuevos, `FacturaReporteDto.java`, `FacturaService.java`, exporters y handler. Tests `FacturaArcaControllerTests.java`, ampliar búsqueda/reportes/inmutabilidad.

**Consume:** servicio y estado persistido. **Produce:** contrato frontend siguiente.

```java
public record AutorizarArcaRequest(boolean confirmarCuitReceptor) {}
public record FacturaArcaDto(long facturaId, Long numeroLocal, UUID intentoId,
    EstadoIntentoArca estadoFiscal, Long numeroFiscal, int puntoVenta, int tipoComprobante,
    String ambiente, String cae, LocalDate caeVencimiento, String mensaje,
    List<ErrorFiscal> errores, List<ErrorFiscal> observaciones,
    boolean puedeAutorizar, boolean puedeConsultar, boolean puedeModificar,
    boolean puedeAnular, boolean puedeEliminar) {}
```

Sin intento: `estadoFiscal=null`, mensaje Sin informar; permisos reflejan factura y habilitación. CAE heredado sigue bloqueando aunque no tenga intento. Ambiente/serie de intentos históricos no se rellenan desde configuración actual.

- [x] Prueba roja MockMvc:

```java
@Test void intentoInciertoDevuelve202YPermisosBloqueados() throws Exception {
    prepararEstadoIncierto();
    mvc.perform(post("/api/facturas/1/arca/consultar"))
       .andExpect(status().isAccepted())
       .andExpect(jsonPath("$.puedeModificar").value(false));
}
```

- [x] Implementar POST autorizar con body de confirmación de CUIT (no datos fiscales editables), GET estado sin red y POST consultar. 200 determinado/lectura, 202 pendiente, 400 validación, 404 inexistente, 409 conflicto y 502/503 cuando no existe resultado durable nuevo. No incluir token/sign/XML/rutas en respuesta o logs.
- [x] Ampliar reporte con estadoFiscal/numeroFiscal/vencimiento/permisos; preservar constructor usado por SELECT_REPORTE y enriquecer intentos por lote de IDs de la página (una consulta, sin N+1). Exportaciones completas también enriquecen por lotes acotados; filtros existentes no cambian significado y sinInformarArca continúa siendo ausencia CAE.
- [x] Cambiar etiqueta N° Factura a N° local y agregar N° fiscal/estado fiscal en PDF/Excel. Borrador aparece Sin CAE; aceptación debe mostrar punto/tipo/número fiscal. Conservar importes y filtros. Tests PDF/XLSX comprueban datos fiscales y borradores.
- [x] Verificar dobles POST no reenvían, estado tras recarga/caída refleja intento, consultar no llama FECAESolicitar, errores no exponen marcadores secretos. Ejecutar tests controlador y regresión de facturas.

## Tarea 6: acción Informar y consulta de resultado en frontend

**Archivos:** DTO y componente Arca nuevos, service/permisos/app/CSS existentes. Pruebas `frontend/tests/AutorizacionArca.test.mjs`, ampliar `FacturaService.test.mjs`, `permisosFactura.test.mjs`, `appDOM.test.mjs`.

**Consume:** JSON tarea 5. **Produce:** acciones de grilla escritorio/móvil y estado fiscal accesible sin llamadas por cada fila al cargar.

- [x] Escribir prueba roja de doble clic/progreso y resultado incierto:

```javascript
it('un doble clic envía una autorización y mantiene bloqueo al quedar incierta', async () => {
  const ui = crearFixtureArca({ estadoFiscal: null });
  ui.confirmarCuit();
  ui.informar.click(); ui.informar.click();
  await ui.responder(202, { estadoFiscal: 'INCIERTO', puedeConsultar: true,
    puedeModificar: false, puedeAnular: false, puedeEliminar: false });
  assert.equal(ui.solicitudesAutorizar(), 1);
  assert.equal(ui.consultar.disabled, false);
  assert.equal(ui.eliminar.disabled, true);
});
```

- [x] `FacturaService`: autorizarArca(id, confirmarCuitReceptor), consultarArca(id), estadoArca(id). Tratar 202 como respuesta válida; sin retry automático de POST. Ante error de red después de enviar, refrescar estado persistido en GET antes de habilitar acciones; si GET falla conservar acciones sensibles bloqueadas y ofrecer recargar.
- [x] Componente reutilizable de acciones/estado, integrado con el mapa de acciones y guardias de mutaciones existente. Confirmación breve accesible: “Homologación. Confirmo que el documento del cliente es un CUIT.” No modificar el número o importe en el navegador. Sin polling permanente ni peticiones N+1 por fila; lectura adicional solo por acción/recuperación.
- [x] Estado comercial separado del fiscal; mostrar PREPARANDO/ENVIANDO/INCIERTO/RECHAZADO/AUTORIZADO y datos número fiscal/CAE/vencimiento. Botón Consultar solo cuando permitido; no ofrecer Reenviar para inciertos. Deshabilitar editar/anular/borrar según permisos backend más guardia CAE heredada. Mantener foco y anunciar progreso/resultado con aria-live.
- [x] Pruebas de rechazo con corrección, CAE inmutable, recarga con intento incierto, fallo de red/estado, teclado y disposiciones móvil/escritorio. Ejecutar `cd frontend && npm test`; inspeccionar flujo en navegador con transporte simulado sin emitir CAE.

## Tarea 7: revisión, aplicación de migración e integración

**Archivos:** guía migración, `docs/arca/HOMOLOGACION.md`, README y HANDOFF del directorio padre.

**Consume:** tareas 1–6 aprobadas por Reviewer. **Produce:** cambios integrados, migración aplicada y evidencia real claramente separada de pruebas.

- [x] Reviewer independiente de SOAP/recuperación y carreras. Resolver hallazgos concretos antes de tocar el esquema del usuario. Sin revisiones simultáneas con edición del mismo archivo.
- [x] Ejecutar perfil PostgreSQL contra esquema temporal: `./mvnw -Parca-postgres -Dtest=MigracionArcaPostgresTests,ReservaArcaPostgresTests test`. Registrar URL saneada, versión PG y resultado, sin secretos. Cleanup solo del esquema aleatorio creado por ese test; nunca DROP public.
- [x] Antes de aplicar a base local: inspección de esquema en lectura, respaldo privado con permisos 0600 fuera de Git, comprobar ausencia de columnas/tablas contradictorias y conexiones de aplicación detenidas. Ejecutar migración transaccional preparada y validar. Cambio aditivo incluido en la autorización del proyecto; respetar aprobación de entorno si la herramienta la solicita.
- [x] Arrancar backend con ddl-auto=validate para esta verificación, sin SQL logging; ejecutar `./mvnw test -Dspring.jpa.hibernate.ddl-auto=validate` y `cd frontend && npm test`, luego `git diff --check`. No asumir que los 145 casos anteriores validan cambios nuevos.
- [ ] Antes de solicitar CAE real de homologación, preparar una factura de prueba identificada por ID y mostrar receptor/importe/fecha/punto (sin secretos) al usuario para acordar el caso concreto. No elegir ni autorizar facturas existentes a ciegas. Una vez acordado, ejecutar autorización y consulta repetida para acreditar idempotencia; si no hay caso aprobado, entregar pruebas simuladas y marcar esa verificación pendiente.
- [x] Documentar método de migración, estados, límites del alcance, recuperación PREPARANDO/INCIERTO y prohibición de reenvío; agregar ejemplos sin datos reales. No proclamar integración productiva ni emisión real si no se comprobó.
- [x] Actualizar HANDOFF con archivos y pruebas observadas. No commit global: separar únicamente archivos de esta etapa si se pide commit y preservar todo trabajo previo.

## Estado del plan y autoverificación

Implementación integrada y migración local aplicada el 03/11/2026. Método multiagente
AGENTS.md conservado. Falta únicamente acordar factura/punto exclusivo y verificar un
CAE real de homologación: no se eligió una factura existente ni se emitió a ciegas.
Cobertura revisada: contrato fiscal/validación (1), persistencia/migración (2), atomicidad/mutaciones (3), envío/recuperación (4), HTTP/reportes (5), UI (6), integración (7).
Los cinco focos de revisión tienen casos de prueba en las tareas indicadas.
Los records/enums y firmas compartidas están definidos arriba; no hay dependencias implícitas en otros agentes.
La confirmación de CUIT en la acción no cambia el valor fiscal del cliente: solo evita interpretar cuitCuil sin confirmación explícita, dentro del alcance aprobado.
El reenvío de estados inciertos queda excluido; no se necesita una regla de repetición implícita para completar este corte.


## Precisiones de implementación y revisión

- Se exige `arca.puntos-venta-exclusivos` antes de autorizar o recuperar. Por defecto vacío,
  sin modificar la configuración privada del usuario para permitir emisión. Resuelve el
  hallazgo de revisión: un comprobante externo con snapshot idéntico no es distinguible por
  WSFE; la exclusividad debe garantizarla el operador de la serie.
- `Consultar` sobre PREPARANDO invalida al ejecutor previo y cierra FALLIDO_ANTES_ENVIO.
  No continúa el despacho: una autorización necesita otra acción explícita. Esto precisa
  la tarea 4 conforme al contrato del endpoint que nunca solicita CAE.
- Catálogo IVA real consultado sin emisión: clases A/ALEY/C, B/C y C/49. El filtro comprueba
  pertenencia de C, con regresión roja/verde sobre el caso real.
- Aceptación actualiza el estado comercial a EMITIDA y conserva cobrado/saldo. Cuatro pruebas
  de circuito completo con JPA y remotos simulados verifican aceptación, timeout recuperable,
  rechazo/nuevo intento y colisión sin adoptar CAE.
- Reportes usan una proyección escalar por lotes de 500 para el estado fiscal, sin N+1.
  Estado visible tras recarga mediante GET a demanda. Mensaje de configuración en grilla;
  rechazo editable no se etiqueta como completamente de solo lectura.


Cierre acordado con el usuario el 03/11/2026: no requiere ejecutar la primera emisión real.
La implementación y migración quedan entregadas con pruebas automatizadas aprobadas;
la casilla de prueba real se conserva pendiente como comprobación opcional, no como bloqueo.
