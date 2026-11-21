# Implementación de Factura B y Factura X

[Diseño aprobado](../../diseno/mockup-facturas-b-x/README.md).

1. **Persistencia y CRUD.** Añadir el tipo B fiscal y el tipo X interno. Probar una migración PostgreSQL que reclasifique las B internas preservando sus datos y un alta y edición de B y X. Persistir el documento del consumidor en la B, exigirlo desde el umbral de ARCA y calcular el IVA del 21 % con los precios finales cuando los artículos tengan la alícuota explícita.
2. **ARCA.** Parametrizar la clase por emisor y tipo de comprobante. Cubrir B y C con pruebas de SOAP, de reservas y de recuperación. Enviar el neto y el IVA de B desde la captura fiscal persistida. Mantener el envío manual y validar el CUIT de la serie en consultas y recuperaciones.
3. **Interfaz y PDF.** Mostrar las tres clases. En B, recoger el documento en el formulario y presentar el IVA contenido. En X, conservar la edición interna. Verificar las acciones por fila y el PDF B autorizado con IVA, CAE y QR, y el PDF X con leyenda interna.
4. **Verificación integrada.** Ejecutar las suites de backend con PostgreSQL y frontend. Migrar una copia de prueba y comprobar altas, edición, guardado local y resultados de ARCA simulados. Verificar el aspecto en Chrome a 390 × 844 y 1440 × 900 y aplicar la migración local cuando los datos cumplan las precondiciones. Para un CAE remoto de B se necesita un emisor responsable inscripto habilitado en homologación.
