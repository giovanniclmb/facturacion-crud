Contrato consultado para las pruebas de autorización, 03/11/2026

- WSDL público homologación: https://wswhomo.afip.gov.ar/wsfev1/service.asmx?WSDL
  Copia guardada `wsfe-homologacion.wsdl` (77307 bytes).
  SHA-256: 16f5b8450c35a2cfc0187f07d6236162bb57b663954a1e1f0267e71afe90af24
- Manual oficial v4.0, revisión 17/03/2025:
  https://arca.gob.ar/ws/documentacion/manuales/manual-desarrollador-ARCA-COMPG-v4-0.pdf
  SHA-256 del PDF consultado: cbe21a67273acd5c096903e77757aeeaeaa1935e12e8a2a03bde0b60033f4a55
  El PDF no se agrega como fixture; se registran su URL, revisión y huella.

El manual v4.0, campo CbteFch (página 26 según numeración PDF), indica
Concepto 1: hasta cinco días anteriores o posteriores, sin exceder el mes
de presentación. En PES, MonCotiz=1. Se informa CondicionIVAReceptorId
con el código del catálogo compatible con clase C, sin asumir el ID local.
La condición de receptor ya aparece en la revisión del 17/03/2025.
Para clase C: sin array Iva; ImpIVA, ImpTotConc e ImpOpEx en cero.
Este corte además excluye tributos y exige ImpNeto=ImpTotal.

Las secuencias de request/response se contrastaron con FEDetRequest,
FECabResponse, FEDetResponse y FECompConsResponse del WSDL guardado.
FECompConsultar devuelve CodAutorizacion/FchVto/Resultado/EmisionTipo y
los datos completos del detalle, más PtoVta y CbteTipo. Su Err602 aislado
significa sin registro; no habilita reenviar.

No se incorporan reglas nuevas del PDF v4.8 fechado 01/12/2026 que ofrece
otro enlace del portal. Los XML de respuesta son sintéticos construidos
en WsfeAutorizacionClientTests; los CUIT se generaron para los tests con
checksum válido y no representan contribuyentes elegidos para emitir.
Ninguna prueba llama a ARCA ni usa credenciales reales.
