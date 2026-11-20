# Formato de impresión

Sistema visual: [revisión 1](../../../design-system/README.md).
Approval: approved (2026-11-19, by user) · A4 tradicional

Visual verify: passed (2026-11-19). Se revisaron los PDF locales de Factura C y Factura B. La tabla implementada conserva la columna Bonif. %. El QR de Factura C se decodificó a 300 ppp y se contrastó con el registro local.

[Abrir ambas opciones](index.html). Datos ficticios. El recuadro QR indica su ubicación y no contiene un código escaneable.

- **A4 tradicional**, recomendado para la demo. Letra del comprobante, emisor y número en la cabecera, receptor en un bloque separado, tabla de ítems, total alineado a la derecha, CAE y QR al pie.
- **A4 compacto.** Usa los mismos datos, con un encabezado de menor altura y una tabla más densa para dar espacio a más ítems.
- El comprobante interno y la copia sin autorización conservan la leyenda **sin validez fiscal** y su estado. La variante fiscal muestra CAE y QR solo cuando existe una autorización confirmada.
- Para pesos argentinos se muestra el símbolo `$` y el formato local. La moneda que se envía a WSFEv1 conserva el código fiscal.

Referencias: [Ejemplo de ARCA](https://arca.gob.ar/fe/qr/ejemplo-de-comprobante.asp) y [vista PDF de Factura C del manual](https://arca.gob.ar/facturadorMovil/documentos/facturadorMovilManual.pdf).

Capturas revisadas a 1440 × 900 y 390 × 844.

- A4 tradicional en [escritorio](previews/tradicional-escritorio.png) y [móvil](previews/tradicional-movil.png).
- A4 compacto en [escritorio](previews/compacta-escritorio.png) y [móvil](previews/compacta-movil.png).
