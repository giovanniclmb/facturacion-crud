# ARCA en homologación: conexión y diagnóstico

Fecha: 03/11/2026. Estado: primer corte implementado y verificado localmente.
Conexión autenticada real verificada el 03/11/2026; ver ajuste al final del documento.

## Objetivo y criterio de cierre

Incorporar la conexión WSAA/WSFEv1 al proyecto existente, primero como un corte
verificable de autenticación y consultas. Con configuración y certificado propios de
homologación, el diagnóstico debe obtener un ticket, consultar el estado del servicio y
el último número autorizado de Factura C para un punto de venta.

El usuario confirmó que todavía falta gestionar certificado y autorización a `wsfe` en
WSASS. La implementación y pruebas locales pueden avanzar; una conexión autenticada
real solo podrá verificarse después de completar esos pasos.

Directorio confirmado por el usuario:
`/Users/giovita/Desktop/UTN/DSOFT/Integrador/arca-homo` (fuera del repositorio
`facturacion-crud`). Contiene `pedido.csr` y `privada.key`; esta última tiene permisos
0600. No se encontró certificado en ese directorio. No se mostró contenido de la clave.
La firma del CSR pasó `openssl req -noout -verify`; eso no acredita todavía un certificado
emitido ni una autorización WSASS, ni verifica por sí solo su correspondencia con la clave.

Este documento desarrolla el diseño acordado en la sección 10.2 del HANDOFF. Conserva
el CRUD, los contratos actuales y el bloqueo completo de facturas con CAE.

## División de la integración

1. **Este corte: conexión y diagnóstico.** Configuración, firma CMS, WSAA, cache de ticket,
   consultas WSFEv1 y endpoint de diagnóstico. Pruebas sin acceso real a ARCA y guía WSASS.
2. **Siguiente corte: autorización recuperable de facturas.** Botón Autorizar, solicitud
   fiscal y persistencia de CAE/rechazo. Su diseño debe registrar un intento antes de
   enviar, conservar la numeración fiscal y resolver resultados inciertos antes de reintentar.

El segundo corte necesita tratar la concurrencia y recuperación, porque el modelo actual
solo registra CAE/resultado/rechazo y no un intento pendiente. Una transacción de base
de datos no deshace una autorización ya emitida por un servicio externo.

## Alternativas evaluadas

- **Recomendada: HttpClient de Java + SOAP/XML acotado + BouncyCastle para CMS.** Encaja
  con Java 21/Spring actual y el diseño previo; permite controlar validaciones, errores y
  pruebas del contrato sin importar el proyecto de ejemplo de la cátedra.
- **Generar clientes desde WSDL.** Aporta tipos generados, pero añade herramientas y un
  conjunto de clases ajenas a la estructura actual; el ejemplo heredado usa `javax`.
- **Servicio intermediario externo.** Añade otra dependencia operativa y gestión de acceso;
  no corresponde al alcance académico acordado de integración directa.

## Configuración y credenciales

- Integración deshabilitada por defecto (`arca.habilitado=false`); el CRUD debe funcionar
  sin certificados y sin efectuar conexiones ARCA durante el arranque.
- `spring.config.import=optional:file:./arca-local.properties`. Archivo local ignorado en
  Git; ejemplo documentado sin CUIT real, certificados, claves ni tickets.
- Parámetros: habilitado, CUIT emisor, ruta absoluta del certificado, clave privada y cache
  de ticket. Los archivos privados se guardarán fuera del repositorio.
- Servicio de autenticación: `wsfe`.
- WSAA: `https://wsaahomo.afip.gov.ar/ws/services/LoginCms`.
- WSFEv1: `https://wswhomo.afip.gov.ar/wsfev1/service.asmx`.
- Los destinos de este corte se restringen a esos endpoints de homologación. Las pruebas
  usan transporte inyectado; no requieren permitir destinos de producción en configuración.
- Al habilitar, validar rutas, lectura, certificado vigente y correspondencia clave/certificado
  antes de usar la conexión. No modificar ni regenerar credenciales existentes.
- No registrar ni devolver clave privada, token, sign, XML autenticado completo o contenido
  del ticket. Evitar representaciones automáticas de objetos que expongan estos datos.

## Componentes y límites

Paquete `com.tp.facturacion.arca`:

- `ArcaProperties`: configuración tipada, validación de habilitación y destinos.
- `SoapHttpClient`: conexión limitada a 10 segundos y solicitud a 30 segundos, SOAP 1.1,
  codificación UTF-8, errores HTTP y Faults; respuestas XML limitadas a 2MiB. Prohibir
  redirecciones HTTP (`Redirect.NEVER`): un 3xx se rechaza sin reenviar autenticación al
  destino de `Location`. La capa SOAP recibe un transporte sustituible
  para probar respuestas sin Internet.
- Utilidades XML: construcción escapada de valores y parseo con namespaces, sin entidades
  externas, DTD, XInclude ni resolución de esquemas remotos. Rechazar XML inválido o sin
  elementos esperados; acotar tamaño de respuestas.
- `TicketAcceso`: token, sign, expiración y comprobación de vigencia con margen de diez
  minutos. Nunca exponerse en un DTO HTTP ni en logs.
- `WsaaClient`: TRA con reloj inyectable, firma CMS con certificado incluido, llamada
  `loginCms` y lectura del XML de respuesta escapado. Soportar claves PEM PKCS#1/PKCS#8
  no cifradas, conforme al formato previsto en el HANDOFF.
- Cache de ticket: memoria y archivo externo, asociado a emisor, servicio, ambiente y
  huella del certificado. Validar formato/vigencia/asociación al cargar. Escritura atómica
  con permisos privados en macOS. Coordinar renovaciones para no solicitar varios tickets
  en paralelo; ante fallo no repetir autenticación indefinidamente.
- `WsfeClient`: `FEDummy`, `FECompUltimoAutorizado`, consulta de puntos de venta y
  `FEParamGetCondicionIvaReceptor` para preparar la validación del siguiente corte.
- `ArcaController`: diagnóstico con DTO explícito, sin entidades ni secretos.
- Excepciones específicas traducidas por `GlobalExceptionHandler` al formato de errores
  existente. Separar configuración local, transporte/protocolo y rechazo de negocio.

Dependencia adicional prevista: BouncyCastle (`bcpkix-jdk18on`) para firma CMS. El plan
de implementación fijará una versión publicada y comprobará su compatibilidad con Java.

## Contrato de diagnóstico

`GET /api/arca/diagnostico?puntoVenta=1`

- Punto de venta requerido, entero entre 1 y 99999; se interpreta como el número fiscal,
  no como ID de una entidad. Se contrasta con los puntos habilitados en homologación.
- Respuesta 200: `ambiente` = `HOMOLOGACION`, `puntoVenta`, `tipoComprobante` = 11,
  estado de servidores (`aplicacion`, `baseDatos`, `autenticacion`), `ticketVencimiento`
  y `ultimoNumeroAutorizado`.
- Si `FEDummy` no reporta los servidores operativos, informar fallo y no presentar la
  conexión como verificada. Un HTTP 200 de SOAP por sí solo no demuestra éxito.
- 400: parámetro inválido o punto no habilitado; 503: integración deshabilitada o problema
  de configuración/credenciales locales; 502: fallo de red, SOAP o respuesta inválida de ARCA.
- Un rechazo de autenticación/punto de venta se presenta con mensaje legible y sanitizado;
  los rechazos de ARCA y Faults upstream responden 502. El 400 por punto no habilitado
  corresponde a la validación local contra una lista oficial obtenida satisfactoriamente.
  Nunca se reemplaza un fallo por datos ficticios.
- El endpoint consulta servicios y puede crear/reutilizar un ticket de acceso. No solicita
  CAE, no cambia facturas ni reserva números; no requiere cambios de esquema.

## Pruebas y aceptación

Primero pruebas que fallen para cada comportamiento nuevo; después implementación.

- Arranque del CRUD con ARCA deshabilitada y sin archivos locales.
- Validación de parámetros, endpoints y habilitación; errores con formato existente.
- XML SOAP válido, namespace/operación correctos, Faults, HTTP no exitoso, redirecciones
  sin seguimiento ni reenvío de credenciales, tiempos límite y límite de tamaño,
  XML truncado/mal formado, XXE/DTD y respuestas con campos obligatorios ausentes.
- Firma CMS verificada contra un certificado/clave efímeros generados solo para pruebas;
  nunca usar credenciales reales ni copiar las del ZIP de la cátedra.
- Ticket válido/vencido/próximo a vencer, cache persistente y de otro emisor/certificado,
  renovación concurrente y errores de lectura/escritura sin filtración de secretos.
- Diagnóstico exitoso y casos de error mediante servicios simulados en el límite de red.
- Regresión completa de backend y frontend. Prueba real de diagnóstico cuando el usuario
  haya terminado WSASS y configurado sus archivos propios.

El corte local se entrega diferenciando claramente pruebas simuladas y verificación real.
No se afirmará que ARCA está conectado si todavía falta certificado/autorización.

## Preparación manual del usuario

1. Reutilizar la clave privada y CSR propios de `Integrador/arca-homo`, que ya existen
   fuera del repositorio `facturacion-crud`; no regenerarlos ni moverlos automáticamente.
   La ruta `~/arca-homo` del HANDOFF debe reemplazarse por la ubicación confirmada.
2. En WSASS, usar **Nuevo Certificado**, un alias propio y el contenido de `pedido.csr`;
   guardar el certificado PEM recibido como `arca-homo/certificado.crt`. Después usar
   **Crear Autorización a Servicio**, elegir ese alias, el CUIT representado y `wsfe`.
   El usuario completa personalmente los accesos con clave fiscal.
3. Guardar certificado y clave fuera de Git y configurar rutas locales. CUIT, rutas y
   datos de acceso reales no deben incluirse en ejemplos, mensajes ni resultados de prueba.
4. Ejecutar el diagnóstico y verificar entorno, punto habilitado y resultado real antes
   de abordar la primera solicitud de CAE.

## Aspectos que debe resolver la autorización posterior

- Separar número provisorio del número fiscal o definir una transición que no confunda
  comprobantes heredados y autorizados. `MAX(numero)+1` local no reserva números ante ARCA.
- Coordinar autorizaciones por emisor/punto/tipo y bloquear edición/eliminación mientras
  exista una solicitud en curso o con resultado incierto.
- Persistir intención y datos enviados antes de la red; registrar aceptación/rechazo en
  transacciones cortas y recuperar por `FECompConsultar` tras timeout o fallo de persistencia.
- Validar identificación del receptor, importes/moneda, fecha, tipo C y condición IVA con
  catálogos oficiales. No asumir que todo consumidor final admite DocTipo99/DocNro0 para
  cualquier importe ni que cualquier código IVA local es válido para Factura C.
- Mantener la regla confirmada por el usuario: una factura con CAE queda completamente
  inmutable, incluida la anulación. No implementar notas de crédito en este primer corte.

## Fuentes y contrato comprobado

- [WSAA oficial](https://www.arca.gob.ar/ws/documentacion/wsaa.asp): certificado de testing
  y asociación a servicio mediante WSASS; endpoint WSAA de homologación.
- Manual WSASS: [crear certificado](https://www.afip.gov.ar/ws/WSASS/html/crearcertificado.html)
  y [crear autorización](https://www.afip.gov.ar/ws/WSASS/html/crearautorizacion.html).
- [Homologación externa](https://www.arca.gob.ar/fe/ayuda/homologacion_externa.asp): catálogo
  oficial de manuales. El portal ofrece un manual v4.8 cuya portada dice 01/12/2026, posterior
  a la fecha de este diseño. No se atribuye vigencia actual a cambios fechados en el futuro.
- [WSDL público de homologación](https://wswhomo.afip.gov.ar/wsfev1/service.asmx?WSDL):
  descargado sin autenticación el 03/11/2026. SHA256
  `16f5b8450c35a2cfc0187f07d6236162bb57b663954a1e1f0267e71afe90af24`.
  Namespace `http://ar.gov.afip.dif.FEV1/`; secuencia verificada de importes en
  `FEDetRequest`: `ImpTotal`, `ImpTotConc`, `ImpNeto`, `ImpOpEx`, `ImpTrib`, `ImpIVA`.
  `CondicionIVAReceptorId` aparece opcional en el esquema; eso no reemplaza las validaciones
  de negocio, por lo que la emisión posterior deberá informarlo y validar su catálogo.

El WSDL público confirma el contrato SOAP descargado, no una conexión autenticada exitosa
ni la disponibilidad operativa de una cuenta/punto de venta concretos.


## Ajuste tras prueba real — 03/11/2026

La conexión autenticada fue verificada. FEParamGetPtosVenta devolvió únicamente 602
(Sin Resultados), mientras FECompUltimoAutorizado respondió correctamente para punto 1,
tipo 11, número 0. El diagnóstico distingue este caso concreto de otros errores remotos:
continúa la consulta y devuelve puntosVentaVerificados=false con advertencia explícita.
No se afirma habilitación para emitir. Catálogos válidos conservan la validación estricta;
otros errores y fallos de consulta siguen sin dar éxito. No se cambió la autorización,
que sigue pendiente de implementar. Esta precisión reemplaza el requisito anterior de
exigir siempre una entrada de catálogo antes de consultar el último comprobante.
