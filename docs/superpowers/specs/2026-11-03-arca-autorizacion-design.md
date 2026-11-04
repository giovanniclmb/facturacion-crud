# Autorización recuperable de Factura C en homologación

Estado: diseño aprobado por el usuario el 03/11/2026. Sin código de autorización ni cambios de esquema ejecutados.
Fecha: 03/11/2026.

## Resultado esperado y alcance

Desde una factura pendiente guardada, el usuario puede ejecutar **Informar a ARCA**,
ver aceptación con número fiscal, CAE y vencimiento, o consultar el rechazo. Si se
pierde la respuesta, puede recuperar el resultado sin generar otra factura.
La conexión autenticada de homologación ya fue comprobada: punto 1, tipo 11, último 0.
El catálogo de puntos devolvió 602; el diagnóstico lo advierte sin acreditar habilitación.

Este corte cubre Factura C (11), venta de productos (Concepto 1), pesos (PES), cotización 1,
emisor monotributista previsto por el proyecto y clientes con CUIT válido (DocTipo 80).
La condición IVA del receptor proviene del catálogo oficial compatible con clase C,
no de asumir que el ID local coincide con el código fiscal. La fecha corresponde a la
factura, sin sustituirla silenciosamente por hoy. Fechas/importes fuera de las reglas
admitidas producen una validación legible antes del envío.

Límite explícito: el modelo actual solo captura cuitCuil y no tipo de documento.
El CUIT receptor debe diferir del CUIT emisor y superar formato/dígito verificador.
CUIL no se interpreta automáticamente como CUIT; documentos de otros tipos, consumidor
final sin identificación, moneda extranjera, servicios, notas de crédito, tipos A/B y
producción quedan fuera de este corte. La interfaz explicará por qué una factura no
puede informarse. Ampliar esos casos necesita datos fiscales explícitos, no inventados.
No se autorizan automáticamente facturas existentes al arrancar o guardar un borrador.

## Alternativas y decisión recomendada

1. Envío directo y guardado posterior de CAE: menor código, pero pierde trazabilidad si
   ARCA autoriza y falla el guardado. Descartado por riesgo de duplicación y edición.
2. Intentos durables y coordinación por serie en la base actual: permite reconstruir
   lo enviado, bloquear carreras y recuperar. Recomendado para este proyecto.
3. Cola y procesador externo: útil a mayor escala; agrega servicios y operación que este
   alcance no necesita. Se mantiene ejecución HTTP con límites, sin transacción abierta
   durante llamadas remotas, y recuperación explícita tras reinicios.

## Número local y número fiscal

`FacturaVenta.numero` sigue identificando la factura local, con filtros y reportes existentes.
Se agregan al resultado fiscal el ambiente, emisor, punto fiscal, tipo 11 y número fiscal.
La UI etiqueta ambos números para evitar confusión. No se renumeran facturas heredadas.
La asignación fiscal usa el último autorizado de ARCA más uno dentro de la reserva durable
de la serie (ambiente, emisor, punto, tipo). MAX(numero) local no participa de esa decisión.
Esta coordinación cubre instancias de esta aplicación; otro sistema usando el mismo punto
puede competir. Una respuesta incompatible se consulta y no se adopta como factura propia.

## Persistencia y coordinación

Nueva entidad de intento fiscal: UUID, FK a factura, serie, número fiscal cuando se conoce,
estado, fechas UTC, identificador de propietario para compare-and-set, snapshot tipado e
inmutable de los datos fiscales, hash canónico, resultado, observaciones y errores saneados.
Importes nuevos se guardan como decimales exactos, con escala fiscal y validación de sumas;
no se copian cálculos binarios double directamente al XML. Se conserva el modelo monetario
heredado fuera de este corte, normalizando/contrastando todos los valores del snapshot.
No se persisten tokens, firmas, claves ni sobres SOAP autenticados.

Una fila de coordinación por serie, con clave única, referencia el intento activo.
Las reservas y cambios de estado usan transacciones cortas con bloqueo de fila y orden
consistente (serie y factura). El envío de red ocurre después del commit. Las mutaciones
locales toman el mismo bloqueo de factura que la preparación de la autorización.
La integridad no depende de un mutex Java ni de deshabilitar un botón.

Un intento activo impide nuevas autorizaciones de esa serie hasta resolverlo, incluso
tras reiniciar. Se limita la espera de bloqueo y se responde conflicto legible.
Una factura con intento activo o aceptado no se edita, anula ni elimina. Para conservar
historial, una factura con intentos fiscales no se borra físicamente; un rechazo confirmado
permite corregir o anular el borrador. Reintentos rechazados conservan su snapshot y no
bloquean permanentemente el número: la unicidad debe aplicarse a reservas vigentes y
aceptadas, no a todos los intentos rechazados que compartan número fiscal.

Las adiciones se entregan como migración explícita, sin borrar datos ni delegar estas
restricciones a ddl-auto=update. Debe validarse en PostgreSQL real además de H2; no adoptar
Flyway con baseline automático de la base existente sin verificar primero su esquema.
El plan de implementación concretará el mecanismo de aplicación de la migración y
recuperación operativa antes de ejecutarla sobre la base local; un rollback de código
no elimina intentos pendientes ni su historial.

## Secuencia de autorización

1. Validar configuración, factura pendiente sin CAE, CUIT, condición IVA de catálogo,
   moneda, productos, fecha, importes y ausencia de otro intento activo.
2. Crear u obtener la fila de serie por su clave única; una carrera en el primer alta
   se resuelve reintentando la transacción tras el conflicto único, no continuando una
   transacción marcada rollback. Bajo el bloqueo de serie y luego el de factura, en una
   sola transacción, comprobar intento activo, crear intento/snapshot y enlazar la reserva.
   El preflight del paso 1 no sustituye este control atómico. Misma factura con intento
   activo devuelve el existente sin despachar; otra factura ocupando la serie recibe 409.
   Si falla antes de reservar, no existe solicitud enviada ni se bloquea el borrador.
3. Consultar último autorizado y asignar número al intento dentro de la reserva.
4. Registrar estado ENVIANDO y commit antes de llamar FECAESolicitar. La transición se
   gana mediante compare-and-set; solo un ejecutor puede despachar ese intento.
5. Enviar exactamente un comprobante con el snapshot guardado.
6. Respuesta aceptada coherente: guardar CAE/vencimiento/número, actualizar factura y
   cerrar reserva en una transacción. Comprobar tipo, punto, número y cardinalidad.
7. Rechazo explícito válido: conservar códigos/mensajes saneados, cerrar intento y
   liberar edición/reserva. Un error de transporte o respuesta incompleta no es rechazo.
8. Timeout, caída del proceso, respuesta contradictoria o fallo al persistir después del
   envío: conservar el intento bloqueado como ENVIANDO/INCIERTO para recuperación.
   Nunca liberar por antigüedad ni crear otra solicitud usando datos editados.

Estados propuestos: PREPARANDO, ENVIANDO, INCIERTO, AUTORIZADO, RECHAZADO y
FALLIDO_ANTES_ENVIO. La recuperación de un PREPARANDO abandonado verifica el propietario;
si ya alcanzó ENVIANDO no se presume que el comprobante no se envió. Sin liberaciones
por tiempo que permitan a un ejecutor tardío enviar después de reemplazar su intento.

## Recuperación y reintentos

La acción **Consultar resultado** usa FECompConsultar con emisor/ambiente/punto/tipo/número
del intento. Si ARCA devuelve el comprobante, contrastar documento, fecha, concepto,
condición IVA, moneda/cotización e importes contra el snapshot.
Si un campo imprescindible no está disponible, no se adopta automáticamente el resultado.
Solo adoptar CAE si la identidad y los datos coinciden. Una colisión se mantiene bloqueada
con mensaje explícito para resolverla; nunca se vincula por número solamente.

Un resultado no encontrado no demuestra que una solicitud todavía en curso no vaya a
ser aceptada. Por defecto se conserva el intento y se permite consultar de nuevo.
El corte no incluye reenvío automático ni un botón que genere nuevo número desde un estado
incierto. El plan deberá precisar una eventual repetición del mismo snapshot como operación
separada; mientras no exista evidencia suficiente no se habilita edición ni otra emisión.
Los rechazos confirmados permiten un nuevo intento tras corregir, volviendo a consultar
la numeración real. Una factura ya autorizada devuelve el resultado guardado sin reenviar.

## Contratos HTTP y UI

- POST `/api/facturas/{id}/arca/autorizar`: sin importes/número/CUIT suministrados por el
  navegador. Crea un intento o devuelve el resultado existente de esa factura.
- GET `/api/facturas/{id}/arca`: estado persistido, último intento, número fiscal,
  CAE/vencimiento, errores y acciones disponibles; no realiza red.
- POST `/api/facturas/{id}/arca/consultar`: reconciliación con ARCA de un intento pendiente;
  no solicita CAE ni crea números nuevos.

Resultado común: identificador del intento, estado fiscal, número local/fiscal, punto,
tipo, CAE/vencimiento cuando existen, observaciones saneadas, mensaje y permisos.
200 para resultado determinado o lectura, 202 para resultado pendiente/incierto ya
persistido, 400 para validación, 404 inexistente, 409 conflicto, 502/503 para fallo remoto
/configuración sin ocultar el estado durable del intento. Un rechazo fiscal explícito
se representa como estado RECHAZADO, no como error de transporte.

La grilla muestra estado fiscal independiente del estado comercial. Se agrega acción
Informar, progreso y Consulta de resultado para los casos inciertos. Botones bloqueados
por solicitud y permisos del backend; recargar reconstruye el estado persistido.
Aceptación muestra número fiscal, CAE y vencimiento. Se conserva inmutabilidad con CAE.
La acción aclara que es homologación. Los reportes identifican el número local y fiscal
sin presentar un borrador como comprobante autorizado.

## Verificación y aceptación

- Pruebas del XML/contrato real WSDL para FECAESolicitar y FECompConsultar, A/R/Fault,
  campos ausentes, incoherencias, observaciones y redacción de información sensible.
- Validaciones fiscales y decimales antes de la solicitud; no inventar documento o IVA.
- Dos solicitudes simultáneas para misma factura/serie: un solo despacho; series distintas
  independientes. Pruebas PostgreSQL para bloqueos/restricciones y migración aditiva.
- Simular autorización remota seguida de fallo de persistencia, timeout y reinicio:
  recuperación desde snapshot, sin doble envío y con edición/borrado bloqueados.
- No encontrado y colisión: sin adopción de CAE ajeno ni liberación insegura.
- Rechazo permite corregir/reintentar sin perder historial; CAE continúa inmutable.
- Pruebas frontend de permisos, doble clic, mensajes, recarga y estados.
- Regresión backend/frontend. Luego prueba de autorización en homologación sobre una
  factura de prueba concreta, documentando que se genera un CAE de prueba.
- Producción sigue técnicamente excluida. No usar datos privados en fixtures/documentos.

## Referencias del proyecto

- `src/main/java/com/tp/facturacion/model/FacturaVenta.java`
- `src/main/java/com/tp/facturacion/service/FacturaService.java`
- `src/main/java/com/tp/facturacion/arca/WsfeClient.java`
- `frontend/src/models/permisosFactura.ts` y `frontend/src/app.ts`
- `docs/diseno/2026-11-03-arca-homologacion.md`
- Guía oficial WSFE: https://www.arca.gob.ar/fe/ayuda/documentos/wsfev1-RG-4291.pdf
- WSDL homologación: https://wswhomo.afip.gov.ar/wsfev1/service.asmx?WSDL

La portada del manual ofrecido por el portal está fechada 01/12/2026, posterior a esta
sesión; no se da por vigente una validación futura. Contrato SOAP y reglas de negocio
vigentes deben quedar contrastados explícitamente en el plan y las pruebas.


## Contraste de fuentes oficiales

La investigación independiente contrastó el manual oficial anterior a esta sesión:
https://arca.gob.ar/ws/documentacion/manuales/manual-desarrollador-ARCA-COMPG-v4-0.pdf
El catálogo de documentación https://arca.gob.ar/fe/ayuda/webservice.asp lista v4.7;
la versión concreta vigente y su contrato se fijarán en el plan, sin usar por defecto v4.8.
Para C: no array IVA; ImpIVA, ImpTotConc e ImpOpEx en cero; sin tributos en el modelo
actual, ImpTrib=0 e ImpNeto=ImpTotal. No se presupone que consumidor final sea el único
receptor válido de Factura C. CondicionIVAReceptorId se informa explícitamente.
Concepto 1 exige contrastar fecha con la ventana y mes previstos por el manual vigente.

Referencia de la obligación de condición IVA: RG 5616/2024, con especificación WS
obligatoria desde 15/04/2025:
https://biblioteca.arca.gob.ar/search/query/norma.aspx?p=t%3ARAG%7Cn%3A5616%7Co%3A9%7Ca%3A2024%7Cf%3A17%2F12%2F2024

FAQ oficial de recuperación ante falta de respuesta:
https://www.afip.gob.ar/genericos/guiavirtual/consultas_detalle.aspx?id=14570227
El 602 de FECompConsultar significa que no existen datos en esa consulta; no es la misma
operación que el 602 de catálogo de puntos y no debe atraparse con su excepción.
