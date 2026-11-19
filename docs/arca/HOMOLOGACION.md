# Conexión y diagnóstico ARCA en homologación

El proyecto incluye autenticación WSAA, consultas WSFEv1, autorización de Factura C y
recuperación de resultados inciertos, solo en homologación. Arranca con ARCA deshabilitada
por defecto. El flujo explícito de guardado de Factura C solicita el CAE después de confirmar la operación local.

## Preparación en WSASS

El usuario confirmó como directorio propio:
`/Users/giovita/Desktop/UTN/DSOFT/Integrador/arca-homo`, fuera del repositorio
`facturacion-crud`. Ya contiene `privada.key` y `pedido.csr`. Reutilizar esos archivos.

1. Ingresar personalmente con clave fiscal a WSASS de homologación.
2. En **Nuevo Certificado**, elegir un alias y pegar el contenido de `pedido.csr`.
   Usar el certificado PEM recibido para crear `certificado.crt` en la misma carpeta.
3. En **Crear Autorización a Servicio**, elegir el alias, CUIT representado y servicio `wsfe`.

Referencias oficiales: [certificado](https://www.afip.gov.ar/ws/WSASS/html/crearcertificado.html)
y [autorización](https://www.afip.gov.ar/ws/WSASS/html/crearautorizacion.html).
La firma válida de un CSR no significa que ya exista un certificado/autorización.

## Configuración local

Desde la raíz `facturacion-crud`, crear el archivo local si aún no existe:

```bash
test -e arca-local.properties || cp arca-local.properties.example arca-local.properties
```

El archivo local se ignora en Git. Su ejemplo obtiene valores del entorno del proceso
Java. Completar únicamente en la terminal local, sin pegar CUIT ni claves en el chat.
Ejemplo para zsh de macOS, desde la raíz del repositorio:

```zsh
read -r "ARCA_CUIT?CUIT emisor para homologación: "
export ARCA_CUIT
export ARCA_CERTIFICADO="$PWD/../arca-homo/certificado.crt"
export ARCA_CLAVE_PRIVADA="$PWD/../arca-homo/privada.key"
export ARCA_TICKET_CACHE="$PWD/../arca-homo/ticket-wsfe.properties"
./mvnw spring-boot:run
```

Alternativamente, completar esos valores directamente en `arca-local.properties`
mediante un editor local. No sobrescribir la clave ni el CSR. El certificado debe estar
vigente y corresponder a la clave; este corte admite claves RSA PEM PKCS#1/PKCS#8 sin cifrar.
El cache contiene un ticket privado y se genera durante la autenticación, no debe crearse
como archivo vacío ni elegirse la misma ruta de la clave o certificado.

Si falta certificado o autorización, mantener `arca.habilitado=false` y usar el CRUD.
Las pruebas locales no necesitan credenciales reales. La configuración habilitada e
incompleta produce un error al intentar el diagnóstico y no realiza una autenticación.

## Diagnóstico

```bash
curl -i 'http://localhost:8080/api/arca/diagnostico?puntoVenta=1'
```

`puntoVenta` es el **número fiscal**, no el ID local. El diagnóstico consulta servidores,
obtiene/reutiliza el ticket, consulta los puntos de venta y el último comprobante tipo 11.
Si el catálogo devuelve datos, exige un punto CAE sin bloqueo ni baja. Si ARCA devuelve
únicamente el código 602 (Sin Resultados), continúa la consulta del último número y
responde con `puntosVentaVerificados=false` y una advertencia: esto verifica la consulta,
pero no acredita que el punto esté habilitado para emitir. Los demás errores siguen
siendo fallos. No reserva el número siguiente.

Una respuesta200 contiene:

| Campo | Significado |
| --- | --- |
| `ambiente` | `HOMOLOGACION` |
| `puntoVenta` | Número consultado |
| `tipoComprobante` | 11, Factura C |
| `servidores` | Estado de aplicación, base de datos y autenticación |
| `ticketVencimiento` | Expiración del ticket, sin token ni sign |
| `ultimoNumeroAutorizado` | Último número informado por ARCA para ese punto/tipo |
| `puntosVentaVerificados` | Si se pudo validar el punto contra el catálogo |
| `advertencias` | Limitaciones del diagnóstico, incluida la respuesta 602 del catálogo |

Los errores mantienen `{status, mensaje, detalles, fecha}`:

- **400:** parámetro inválido o punto no habilitado según un catálogo recibido correctamente.
- **503:** ARCA deshabilitada, configuración/archivos locales inválidos o cache no utilizable.
- **502:** transporte, tiempo límite, rechazo SOAP/WSAA/WSFE o respuesta inválida.

Los mensajes públicos se redactan para no filtrar rutas privadas, token, sign, clave o
respuestas autenticadas. No se muestra ni se registra el sobre SOAP completo.

## Ticket y operaciones de red

El cliente reutiliza un ticket con más de diez minutos de vigencia, en memoria y en
archivo privado. El cache está asociado al emisor, servicio, endpoints y certificado;
escribirlo es atómico y la renovación se coordina con bloqueos por archivo/proceso.
No editar el cache ni compartirlo. Ante un archivo corrupto o de otra identidad se
informa error; no se reemplaza ciegamente ni se solicitan tickets en bucle.
Si falla guardar un ticket recién obtenido, se mantiene en memoria para reintentar la
persistencia sin otro login en esa instancia; el diagnóstico sigue respondiendo503 hasta
guardar correctamente. Tras un fallo remoto de autenticación hay una espera lógica de60s
antes de permitir otro intento; no se bloquea el hilo con una espera de red artificial.
Si cambia CUIT/certificado/ambiente, elegir otra ruta de cache en vez de sobrescribir el
archivo asociado a una identidad diferente.

Solo se admiten los destinos de homologación del ejemplo. No se siguen redirecciones.
La conexión tiene límite de 10 s, la solicitud de 30 s y el XML de 2 MiB. No se conectan servicios al
arrancar la aplicación; la red se usa al pedir el diagnóstico.

## Pruebas y alcance de la verificación

```bash
./mvnw test -Dspring.jpa.hibernate.ddl-auto=validate
cd frontend
npm test
```

Las pruebas nuevas generan claves/certificados efímeros y respuestas SOAP sintéticas,
y usan servidores loopback para verificar transporte cuando corresponde. No leen la
carpeta privada del usuario ni solicitan tickets reales. Las pruebas JPA heredadas usan
H2 aislado; `contextLoads` requiere el PostgreSQL local, con validación sin actualizar esquema.

La validación real queda pendiente hasta terminar WSASS y ejecutar el diagnóstico con
archivos propios. No confundir pruebas simuladas aprobadas con una cuenta ARCA conectada.

Verificación del 03/11/2026: suite completa de 140 pruebas backend y 21 frontend, sin fallos.
Incluye 75 casos nuevos ARCA (configuración, HTTP/XML, CMS, cache/WSAA, WSFE y diagnóstico)
y las 65 pruebas backend anteriores. Las revisiones independientes detectaron dos casos
de cache que se corrigieron y verificaron antes de la regresión final.

## Autorización de Factura C

Aplicar primero la [migración aditiva](MIGRACION_AUTORIZACION.md). La aplicación valida
el esquema al arrancar; no crea las tablas de intentos automáticamente.

Alcance: productos, pesos (PES), cotización 1, receptor con CUIT válido distinto del
emisor y condición IVA habilitada para clase C. Se contrasta la clase como miembro de
las combinaciones oficiales (`B/C`, `C/49`, `A/ALEY/C`), sin asumir que el catálogo dice
únicamente C. No se convierten CUIL u otros documentos en CUIT por suposición.

Antes de habilitar emisiones, reservar un punto para uso exclusivo de esta aplicación
para el emisor/tipo. WSFE no tiene clave de correlación de cliente; si otro sistema
emite un comprobante con exactamente los mismos datos fiscales no hay forma de distinguirlo
por consulta. La exclusividad es una precondición operativa, no una garantía que ARCA
verifique para la aplicación. Una vez confirmada, configurar solo localmente:

```properties
arca.puntos-venta-exclusivos=1
```

Sin esa declaración, diagnóstico y CRUD siguen disponibles, pero emisión y recuperación
remota quedan bloqueadas. El ejemplo admite `ARCA_PUNTOS_VENTA_EXCLUSIVOS`; el archivo
local ya existente debe incorporar la propiedad manualmente cuando se cumpla la condición.
No habilitar producción ni reutilizar un punto compartido para superar la validación.

En la grilla, seleccionar la confirmación de CUIT y **Informar a ARCA**. La aplicación
asigna el número fiscal dentro de la reserva, registra el intento y solicita CAE.
El número local se conserva. El estado fiscal se muestra separado del comercial.

- PREPARANDO: reserva registrada, sin despacho confirmado.
- ENVIANDO/INCIERTO: edición, anulación y eliminación bloqueadas; usar Consultar ARCA.
- AUTORIZADO: número fiscal, CAE y vencimiento persistidos; pasa a EMITIDA y queda inmutable.
- RECHAZADO: puede corregirse/anularse y crear otro intento; conserva historia y no se borra.
- FALLIDO_ANTES_ENVIO: preparación cerrada sin despacho; puede iniciarse otra autorización.

Consultar nunca solicita CAE. Si encuentra PREPARANDO invalida al propietario anterior y
cierra la preparación como FALLIDO_ANTES_ENVIO; autorizar de nuevo exige otra acción explícita.
Si ARCA no encuentra un ENVIANDO/INCIERTO (602 de FECompConsultar), conserva el bloqueo.
Nunca liberar por antigüedad ni reenviar automáticamente. Si encuentra un comprobante con
datos diferentes del snapshot, no adopta ese CAE y mantiene el conflicto para conciliación.

### API

- POST `/api/facturas/{id}/arca/autorizar`, JSON `{"confirmarCuitReceptor":true}`.
- GET `/api/facturas/{id}/arca`: solo estado persistido, sin red.
- POST `/api/facturas/{id}/arca/consultar`: consulta y conciliación, sin emisión.

200 para resultado determinado/lectura, 202 para resultado pendiente, 400 validación,
404 inexistente, 409 conflicto y 502/503 para fallos remotos/configuración cuando no hay
un resultado durable nuevo. Un rechazo fiscal explícito es un resultado RECHAZADO, no 502.
Tras un fallo de red del navegador, se relee el estado antes de habilitar acciones.
Si también falla esa lectura, los botones sensibles siguen bloqueados hasta recuperar el estado.



## Primera prueba autenticada — 03/11/2026

Certificado y autorización WSASS configurados localmente. El diagnóstico real respondió
HTTP 200: servidores OK, punto 1, tipo 11 y último comprobante 0. La consulta de puntos
de venta devolvió 602 (Sin Resultados); se expone como advertencia y no se acredita
habilitación para emitir. Se corrigió esta distinción con cinco pruebas nuevas y revisión
independiente; regresión backend de 145 pruebas aprobada. No se solicitó CAE ni se
modificaron facturas o esquema. Los archivos locales y el ticket permanecen privados.


## Verificación de autorización — 03/11/2026

Implementación y migración local verificadas: 208 pruebas backend (incluidas cuatro de
PostgreSQL real en esquemas aislados) y 34 frontend, sin fallos ni omisiones. Se comprobó
arranque con validación de esquema, grilla HTTP 200 y estado fiscal HTTP 200.
La base mantiene 10 facturas y sus totales; no hay intentos reales creados. El catálogo IVA
se consultó de forma autenticada para contrastar las clases combinadas. No se solicitó
ningún CAE en esta etapa: falta acordar la factura de prueba y el punto exclusivo.


El usuario aceptó cerrar la integración sin exigir una primera emisión real (03/11/2026).
La comprobación de CAE real de homologación es opcional y no bloquea la entrega del código.
No se requiere completar los datos del receptor mientras no se decida realizarla.


Revisión de consignas del 03/11/2026: 218 pruebas backend y 35 frontend aprobadas,
con pruebas PostgreSQL habilitadas y sin omisiones. El informe general de arquitectura
y cumplimiento se entrega fuera del repositorio, en Integrador/docs, por pedido del usuario.
La guía se sigue con receptor CUIT (DocTipo80) según el alcance aprobado; el ejemplo
conceptual DocTipo99/0 no se implementa como fallback para documentos inválidos.


## Preparar la demo en otro equipo

1. Preparar la base local del integrador con los catálogos y el usuario de sistema de ID `1`. Aplicar las migraciones de esquema según [las instrucciones del repositorio](../../README.md).
2. Desde la raíz del proyecto, cargar los receptores y los precios de ejemplo:

   ```sh
   psql -X -v ON_ERROR_STOP=1 -d facturacion_crud -f db/demo/clientes_arca.sql
   psql -X -v ON_ERROR_STOP=1 -d facturacion_crud -f db/demo/precios_mayorista.sql
   ```

3. Configurar en ese equipo la conexión WSASS de homologación con su certificado, clave y autorización. Cargar en `punto_venta` el número fiscal habilitado y declararlo en `arca.puntos-venta-exclusivos` si queda reservado a esta aplicación. El `99001` se usó con el emisor de la demostración original.
4. En **Nueva factura**, seleccionar **Factura C**, el punto configurado, **Pesos**, **Responsable Inscripto** y uno de los clientes **Cliente demostración ARCA** o **Giovanni Colombo (demo)**. Ambos registros locales representan al mismo receptor de ejemplo `30000000007`, tomado del [manual de WSFEv1](https://www.arca.gob.ar/ws/documentacion/manuales/manual-desarrollador-ARCA-COMPG.pdf). Agregar un artículo, confirmar el CUIT y pulsar **Guardar y solicitar CAE**.

La carga de clientes conserva los registros existentes. Si encuentra alguno de los dos nombres con un documento diferente o dado de baja, revierte la transacción e informa cuál debe revisarse. Los comprobantes y CAE se generan al realizar la prueba desde ese equipo.

## Emisión de demostración y verificación remota

El 18/11/2026 se emitió desde la interfaz la Factura C de homologación
**99001-00000001**, ID local **73** en la base separada
`facturacion_demo_correcciones_20261118`. Se usaron pesos y un cliente de demostración
cargado con un CUIT de ejemplo del manual de ARCA. El catálogo de puntos había devuelto
602, pero el punto aceptó la solicitud. Una llamada posterior a `FECompConsultar` confirmó
el mismo número, receptor, total, CAE y vencimiento del intento persistido. El QR se leyó
del PDF A4 generado y se contrastó con ese registro. La factura de prueba permanece en
la copia local.

Para repetir únicamente la consulta del comprobante, configurar el datasource de la
copia y el ID local ya autorizado. Esta prueba no solicita otro CAE:

```sh
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/facturacion_demo_correcciones_20261118 \
ARCA_VERIFICAR_FACTURA_ID=73 \
./mvnw -Dtest=ConsultaHomologacionManualTests test
```

El guardado explícito de Factura C reemplaza el paso manual para las nuevas altas o para
una edición elegible. La acción de consulta sigue disponible para resultados inciertos.
El botón manual histórico y los contratos REST previos se mantienen para sus consumidores.
