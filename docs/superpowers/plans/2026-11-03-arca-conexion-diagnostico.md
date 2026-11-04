# ARCA: conexión y diagnóstico — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Incorporar autenticación WSAA, consultas WSFEv1 y diagnóstico de homologación,
con pruebas locales y sin emitir CAE ni modificar facturas.

**Architecture:** Clientes SOAP pequeños sobre transporte inyectable, XML seguro y firma
CMS. La configuración está deshabilitada por defecto y las credenciales se leen al usar
la integración. El ticket se conserva en memoria y archivo privado para reutilizarlo.

**Tech Stack:** Java 21, Spring Boot existente, JDK HttpClient/SOAP 1.1, BouncyCastle
`org.bouncycastle:bcpkix-jdk18on:1.86`, JUnit/Mockito ya disponibles.

**Spec:** [Diseño aprobado](../../diseno/2026-11-03-arca-homologacion.md).

## Global Constraints

- Solo homologación: WSAA `https://wsaahomo.afip.gov.ar/ws/services/LoginCms` y
  WSFEv1 `https://wswhomo.afip.gov.ar/wsfev1/service.asmx`; servicio `wsfe`.
- Deshabilitada por defecto (`arca.habilitado=false`); CRUD funciona sin archivos privados
  ni conexiones ARCA al arrancar. Constructores de componentes sin I/O ni llamadas remotas.
- Conexión limitada a 10 segundos y solicitud a 30 segundos; respuestas XML de hasta 2MiB.
- Prohibir redirecciones HTTP (`Redirect.NEVER`) y rechazar 3xx sin reenviar autenticación.
- Parseo sin entidades externas, DTD, XInclude ni resolución de esquemas remotos.
- Margen de vigencia del ticket de diez minutos; certificado y clave fuera de Git.
- No registrar ni devolver clave privada, token, sign, XML autenticado completo o ticket.
- Mensajes/código propios en español; no cambiar contratos de facturas, numeración,
  reglas CAE, datos de PostgreSQL ni el frontend en este corte.
- Reutilizar `Integrador/arca-homo/privada.key` y `pedido.csr`; falta obtener certificado
  y autorización WSASS. No generar ni sobrescribir esos archivos durante pruebas.
- Modelo/razonamiento por roles conforme al AGENTS.md global: Worker Sol medium,
  Reviewer Luna medium, coordinación e integración en esta sesión. Preservar todos los
  cambios existentes; checkpoints revisables por tarea, sin commits que incluyan trabajo ajeno.

## Review Focus

1. Un certificado de otro alias, vencido o que no corresponda a la clave debe fallar
   localmente sin enviar una solicitud ni revelar material privado: tarea 3.
2. Un HTTP 200 con SOAP Fault, negocio rechazado o campos faltantes no debe convertirse
   en diagnóstico exitoso: tareas 2 y 5.
3. Cache de otra identidad, vencido, corrupto o con fallo de escritura no debe mezclarse
   con otro emisor ni disparar un bucle de autenticación: tarea 4.
4. Dos diagnósticos concurrentes o dos procesos con el mismo cache no deben pedir dos
   tickets simultáneos: tarea 4, coordinación local más bloqueo de archivo.
5. ARCA deshabilitada o mal configurada no debe impedir el CRUD ni hacer conexiones al
   iniciar; diagnóstico debe responder 503 y mantener el formato de errores: tareas 1 y 6.

---

## Estructura y propiedad de archivos

Todo el cliente vive en `src/main/java/com/tp/facturacion/arca/`. Cada clase tiene una
responsabilidad; las firmas siguientes son contratos entre tareas y deben conservarse.

| Archivos | Responsabilidad |
| --- | --- |
| `ArcaProperties.java`, `ArcaConfiguration.java` | Configuración tipada y beans sin I/O inicial |
| `ArcaConfiguracionException.java`, `ArcaRemotaException.java` | Fallos locales 503 y upstream 502 |
| `TransporteSoap.java`, `RespuestaSoap.java`, `HttpTransporteSoap.java` | HTTP limitado e inyectable |
| `XmlSeguro.java`, `SoapHttpClient.java` | XML/SOAP y validación de respuestas |
| `CredencialesArca.java`, `FirmadorCms.java` | Lectura PEM, validaciones y firma verificable |
| `TicketAcceso.java`, `AlmacenTicket.java`, `WsaaClient.java` | Renovación coordinada y cache privado |
| `EstadoServidores.java`, `PuntoVentaArca.java`, `CondicionIvaReceptor.java`, `WsfeClient.java` | Consultas WSFE y DTOs internos |
| `ArcaDiagnosticoDto.java`, `ArcaDiagnosticoService.java` | Orquestación y resultado público seguro |
| `controller/ArcaController.java` | Endpoint de diagnóstico |

Cambios compartidos reservados al integrador: `pom.xml`,
`FacturacionApiApplication.java`, `application.properties`, `GlobalExceptionHandler.java`,
`.gitignore`, README y HANDOFF. No editar simultáneamente esos archivos.

Los tests nuevos van en `src/test/java/com/tp/facturacion/arca/`; las respuestas SOAP
de prueba en `src/test/resources/arca/`. Usar XML sintético sin credenciales reales.

## Tarea 1: configuración, dependencia y errores

**Archivos:** crear `arca/ArcaProperties.java`, `ArcaConfiguration.java` y las dos
excepciones; modificar los archivos compartidos indicados; crear `ArcaPropertiesTests.java`
y `ArcaDeshabilitadaTests.java`.

**Interfaces:**

```java
// @ConfigurationProperties("arca"); getters/setters, sin toString de secretos.
public class ArcaProperties {
    // habilitado=false; cuit, certificado, clavePrivada, ticketCache;
    // wsaaUrl/wsfeUrl con los valores exactos de Global Constraints.
    public void exigirHabilitada(); // 503 si false
    public void validarConfiguracion(); // 503 si faltan datos o destino no permitido
}
// Excepciones RuntimeException; mensajes locales constantes/sanitizados.
// ArcaConfiguration produce Clock.systemUTC() y HttpClient configurado; sin red.
```

- [x] Escribir tests: configuración ausente permite arrancar el contexto; invocar
  `exigirHabilitada()` falla con mensaje local; habilitada sin rutas falla; ambos destinos
  de producción y una URI con usuario/query/fragmento fallan sin tocar archivos/red.

```java
@Test void porDefectoNoPermiteUsarArca() {
    var propiedades = new ArcaProperties();
    assertThrows(ArcaConfiguracionException.class, propiedades::exigirHabilitada);
}
```

- [x] Ejecutar `./mvnw -Dtest=ArcaPropertiesTests,ArcaDeshabilitadaTests test`; observar
  rojo por clases/comportamiento ausente antes de implementar.
- [x] Agregar dependencia fijada y `@ConfigurationPropertiesScan`. Configurar import
  opcional local y `arca.habilitado=false`; validar CUIT como 11 dígitos, rutas absolutas
  y endpoints exactos solo al usar la integración. No leer archivos en constructores.

```xml
<dependency>
  <groupId>org.bouncycastle</groupId>
  <artifactId>bcpkix-jdk18on</artifactId>
  <version>1.86</version>
</dependency>
```

- [x] Mapear excepciones a `ErrorResponse` 503/502 antes del handler genérico; no loguear
  cuerpos SOAP ni causas con contenido sensible. Agregar patrones de exclusión para
  claves/certificados/CSR/tickets sin ocultar fixtures XML de tests.
- [x] Reejecutar tests y revisar diff/checkpoint de esta tarea.

## Tarea 2: transporte HTTP y parseo SOAP seguro

**Archivos:** crear `TransporteSoap.java`, `RespuestaSoap.java`, `HttpTransporteSoap.java`,
`XmlSeguro.java`, `SoapHttpClient.java`; tests `HttpTransporteSoapTests.java`,
`XmlSeguroTests.java`, `SoapHttpClientTests.java`.

**Interfaces consumidas:** excepciones/configuración de tarea 1. **Producidas:**

```java
public interface TransporteSoap {
    RespuestaSoap enviar(URI destino, String accion, String sobre);
}
public record RespuestaSoap(int status, byte[] cuerpo) {}
public final class XmlSeguro {
    public static Document parsear(String xml);
    public static String escapar(String valor);
    public static Element exigirElemento(Element padre, String namespace, String nombre);
}
public class SoapHttpClient {
    public Document llamar(URI destino, String accion, String cuerpoXml);
}
```

- [x] Escribir tests con XML sintético válido, Fault SOAP 1.1, respuesta truncada, DTD/XXE,
  raíz incorrecta y falta de Body/operación. Incluir marcador secreto en un Fault y afirmar
  que el mensaje de excepción no lo contiene.

```java
@Test void rechazaDtdAntesDeResolverEntidades() {
    String xml = "<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///archivo-que-no-debe-leerse'>]>"
            + "<x>&e;</x>";
    assertThrows(ArcaRemotaException.class, () -> XmlSeguro.parsear(xml));
}
```

- [x] Correr los tres tests nuevos y observar rojo.
- [x] Configurar HttpClient con timeout de conexión 10s y `Redirect.NEVER`; solicitud
  POST UTF-8, `Content-Type: text/xml; charset=utf-8`, `SOAPAction` entre comillas y timeout30s.
  Leer `InputStream` hasta 2MiB+1 y cerrar siempre; rechazar exceso, status no2xx y 3xx.
  Restaurar interrupción de hilo al traducir `InterruptedException`.

```java
HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(10))
    .followRedirects(HttpClient.Redirect.NEVER)
    .build();
```

- [x] Construir/parsear el sobre con namespace SOAP1.1; deshabilitar DTD, entidades,
  acceso externo a esquemas y XInclude. No extraer datos usando un nombre global ambiguo:
  exigir estructura/namespace esperados y unicidad de elementos requeridos.
- [x] Probar redirección con servidor HTTP local de test: origen302 con Location a otra
  ruta, contador de destino permanece0. Esta URI se usa en el test del transporte genérico,
  no en propiedades ARCA. Probar timeout/error HTTP con transporte simulado y exceso de2MiB.
- [x] Correr tests; revisar diff/checkpoint y contrato para tareas siguientes.

## Tarea 3: credenciales propias y firma CMS

**Archivos:** crear `CredencialesArca.java`, `FirmadorCms.java`; tests
`CredencialesArcaTests.java`, `FirmadorCmsTests.java`, helper `CredencialesPrueba.java`.

**Interfaces:**

```java
public final class CredencialesArca {
    public static CredencialesArca cargar(ArcaProperties propiedades, Clock reloj);
    public X509Certificate certificado();
    public String huellaCertificado(); // SHA256, nunca clave/token
    PrivateKey clavePrivada(); // acceso de paquete para FirmadorCms, nunca texto PEM
    // Clave privada interna, sin getters públicos de texto ni toString sensible.
}
public class FirmadorCms {
    public String firmarBase64(String tra, CredencialesArca credenciales);
}
```

- [x] Generar en tests un par RSA y certificado efímeros; escribir PEM solo en `@TempDir`.
  Cubrir PKCS#1/PKCS#8, archivo ausente, PEM inesperado/cifrado, certificado vencido/futuro
  y par clave/certificado incompatible. No abrir la carpeta real del usuario.
- [x] Verificar la firma producida, contenido TRA encapsulado y certificado incluido:

```java
var cms = new CMSSignedData(Base64.getDecoder().decode(firmaBase64));
assertArrayEquals(tra.getBytes(StandardCharsets.UTF_8),
        (byte[]) cms.getSignedContent().getContent());
// Verificar cada SignerInformation con el certificado efímero del test.
```

- [x] Ejecutar tests, observar rojo y luego implementar con PEMParser/conversores BC,
  `SHA256withRSA`, UTF-8 y CMS encapsulado. Verificar correspondencia firmando/verificando
  un desafío local antes de permitir una solicitud WSAA.
- [x] Devolver503 por problemas locales, sin contenido PEM/rutas privadas completas en
  mensajes públicos. Reejecutar tests; revisar diff/checkpoint.

## Tarea 4: ticket, cache persistente y WSAA

**Archivos:** crear `TicketAcceso.java`, `AlmacenTicket.java`, `WsaaClient.java`; tests
`TicketAccesoTests.java`, `AlmacenTicketTests.java`, `WsaaClientTests.java`.

**Interfaces consumidas:** reloj/configuración/firma/SOAP de tareas1–3. **Producidas:**

```java
public final class TicketAcceso {
    public TicketAcceso(String token, String sign, Instant expiracion);
    public String token();
    public String sign();
    public Instant expiracion();
    public boolean vigente(Clock reloj);
    // toString redactado: no token/sign.
}
public class WsaaClient {
    public TicketAcceso obtenerTicket();
}
public class AlmacenTicket {
    // Identidad: huella de CUIT/servicio/endpoints/certificado; ruta desde configuración.
    public TicketAcceso obtener(String identidad, Clock reloj, Supplier<TicketAcceso> renovar);
}
```

- [x] Escribir tests de frontera: expiración ahora+10min no vigente, +10min+1s vigente;
  token/sign vacíos inválidos. Un ticket y sus excepciones no exponen marcadores secretos.
- [x] Escribir tests de WSAA con transporte simulado y XML escapado de `loginCmsReturn`:
  ticket correcto, campos ausentes, expiración inválida, fault y errores sin bucle de retry.
- [x] Escribir tests de cache en `@TempDir`: reinicio de cliente reutiliza ticket válido;
  cache de otra identidad/certificado/servicio no se reutiliza; cache corrupto o no legible
  produce503 recuperable, no se sobrescribe ciegamente; archivos creados con0600 en POSIX.
- [x] Ejecutar tests y observar rojo.
- [x] Implementar TRA con namespace/operación WSAA, uniqueId desde reloj/contador, generación
  ahora−10min y expiración ahora+10min; servicio `wsfe`; `loginCms/in0`, SOAPAction vacía.
  Namespace WSAA: `http://wsaa.view.sua.dvadac.desein.afip.gov`.
  Leer únicamente `loginCmsResponse/loginCmsReturn` del namespace WSAA y parsear el XML interno.
- [x] Cache privado mediante `Properties` UTF-8: identidad compuesta de CUIT, servicio,
  endpoints y huella SHA256 de certificado; token/sign/expiración. Guardar primero en archivo
  temporal0600 y mover atómicamente. Un fallo de persistencia genera503 sin mostrar secretos.
- [x] Coordinar lectura/renovación con mutex por ruta normalizada dentro de JVM y lockfile
  privado para procesos. Volver a leer cache después de obtener el lock. Limitar espera del
  lock a30s; agotarla produce503. No mantener un lock global entre caches distintos.
- [x] Test concurrente con varias llamadas y dos instancias sobre la misma ruta: exactamente
  una solicitud loginCms, resto reutiliza ticket. Probar fallo de escritura y lock ocupado;
  siempre liberar recursos en finally. Reejecutar tests; revisar diff/checkpoint.

## Tarea 5: consultas WSFEv1 y validación de respuestas

**Archivos:** crear `EstadoServidores.java`, `PuntoVentaArca.java`,
`CondicionIvaReceptor.java`, `WsfeClient.java`; test `WsfeClientTests.java` y XML sintético.

**Interfaces:**

```java
public record EstadoServidores(String aplicacion, String baseDatos, String autenticacion) {}
public record PuntoVentaArca(int numero, String emisionTipo, boolean bloqueado, LocalDate fechaBaja) {}
public record CondicionIvaReceptor(int id, String descripcion, String claseComprobante) {}
public class WsfeClient {
    public EstadoServidores estadoServidores();
    public List<PuntoVentaArca> puntosVenta(TicketAcceso ticket);
    public long ultimoAutorizado(TicketAcceso ticket, int puntoVenta, int tipoComprobante);
    public List<CondicionIvaReceptor> condicionesIvaReceptor(TicketAcceso ticket);
}
```

- [x] Escribir tests con cada resultado esperado y errores `Errors/Err`, Fault, ausencia
  de `ResultGet`, elementos duplicados, servidor noOK, tipos numéricos inválidos y lista
  vacía válida. Afirmar que HTTP200 con `Errors` no devuelve un valor fiscal exitoso.
- [x] Escribir tests del límite de transporte que inspeccionen el XML enviado: namespace
  `http://ar.gov.afip.dif.FEV1/`, SOAPAction por operación y valores token/sign/CUIT escapados;
  usar solo marcadores sintéticos. FEDummy no lleva Auth.
- [x] Ejecutar tests y observar rojo; implementar las cuatro operaciones con estructura
  verificada contra WSDL público, sin dependencias JAX-WS ni copiar clases del ZIP.
- [x] `FEParamGetPtosVenta`: leer Nro/EmisionTipo/Bloqueado/FchBaja; aceptar fecha ausente
  o valor vacío/cero documentado como sin baja. Rechazar datos incoherentes; no interpretar
  un string desconocido como habilitado.
- [x] `FEParamGetCondicionIvaReceptor`: leer Id/Desc/Cmp_Clase; consultar catálogo sin
  filtrar clase en este corte. `FECompUltimoAutorizado`: requerir CbteNro>=0 y no tratar un
  elemento ausente como0. Revisar diff/checkpoint tras tests verdes.

## Tarea 6: diagnóstico integrado y documentación de uso

**Archivos:** crear `ArcaDiagnosticoDto.java`, `ArcaDiagnosticoService.java`,
`controller/ArcaController.java`; tests `ArcaDiagnosticoServiceTests.java`,
`ArcaControllerTests.java`; actualizar README/HANDOFF y ejemplo de configuración sin datos reales.

**Interfaces:**

```java
public record ArcaDiagnosticoDto(String ambiente, int puntoVenta, int tipoComprobante,
        EstadoServidores servidores, Instant ticketVencimiento, long ultimoNumeroAutorizado) {}
public class ArcaDiagnosticoService {
    public ArcaDiagnosticoDto diagnosticar(int puntoVenta);
}
// GET /api/arca/diagnostico?puntoVenta=1; devuelve ArcaDiagnosticoDto.
```

- [x] Escribir tests HTTP con MVC, sin PostgreSQL ni ARCA real: parámetro ausente,
  texto/noentero,0 y100000→400; deshabilitada→503; configuración incompleta→503;
  Fault/rechazo upstream→502; punto ausente/baja/bloqueado de catálogo válido→400.
- [x] Test exitoso: FEDummy OK, un ticket válido, punto habilitado para emisión webservices,
  consulta de último número con tipo11 y DTO `HOMOLOGACION`; no token/sign/CUIT/clave en JSON.
- [x] Test servidores noOK:502 y no llamar consultas autenticadas siguientes. Reutilizar
  ticket válido y cero llamadas durante el arranque, incluida la carga del controlador.
- [x] Correr tests, observar rojo e implementar: validar habilitación/configuración y punto;
  FEDummy, ticket, puntos de venta habilitados y último autorizado. Registrar solo resultado
  operativo sin datos de autenticación ni respuesta SOAP completa.
- [x] Documentar la configuración local y diagnóstico; el usuario completa WSASS usando
  su CSR existente. Ejemplo usa variables/nombres ficticios, sin CUIT ni clave real:

```properties
arca.habilitado=true
arca.cuit=${ARCA_CUIT}
arca.certificado=${ARCA_CERTIFICADO}
arca.clave-privada=${ARCA_CLAVE_PRIVADA}
arca.ticket-cache=${ARCA_TICKET_CACHE}
```

- [x] Mantener deshabilitado el proyecto por defecto. Verificar `.gitignore` para el archivo
  local y patrones privados; no copiar archivos desde `Integrador/arca-homo` al repositorio.
- [x] Ejecutar verificaciones integradas:

```bash
./mvnw test -Dspring.jpa.hibernate.ddl-auto=validate
cd frontend
npm test
```

- [x] Leer salida completa y estados de salida. Si el sandbox impide Maven/red/loopback/
  PostgreSQL, reintentar con la escalación correspondiente. Nunca reemplazar un test
  remoto por una simulación sin identificarlo en la entrega.
- [x] Revisión independiente de seguridad y lógica del cliente (credenciales/XML/HTTP/cache),
  corregir hallazgos y repetir solo checks afectados y la integración final necesaria.
- [x] Actualizar estado de documentación: pruebas locales aprobadas y diagnóstico real
  pendiente hasta que exista certificado/autorización. No agregar botón Autorizar en este corte.

## Fuente de la dependencia y verificación del plan

Versión1.86 comprobada como publicada en [BouncyCastle Java](https://www.bouncycastle.org/download/bouncy-castle-java/)
y [Maven Central](https://central.sonatype.com/artifact/org.bouncycastle/bcpkix-jdk18on).
Las versiones1.70 cacheadas de `jdk15on` no se usarán para este módulo nuevo.

Auto-revisión: cada requisito del diseño se asigna a una tarea; firmas de ticket,
transporte y consultas son consistentes; casos de Review Focus tienen tests en sus tareas.
No se autoriza facturación ni se toca el esquema de PostgreSQL en este plan.

## Estado de ejecución

Plan aprobado y ejecutado con el método multiagente de AGENTS.md. Se incorporaron las
seis tareas y se verificaron las correcciones de revisión. Regresión final del03/11/2026:
140 pruebas backend y21 frontend sin fallos; `git diff --check` sin problemas.
Las75 pruebas nuevas ARCA usan credenciales efímeras y respuestas simuladas/loopback.
La prueba autenticada real queda pendiente de certificado/autorización WSASS; este corte
no emite CAE ni modifica facturas.
