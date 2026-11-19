INSERT INTO usuario(id,usuario,clave,nombre,apellido) VALUES(1,'fixture','sintetica','Fixture','Prueba');
INSERT INTO contacto(id) VALUES(1);
INSERT INTO domicilio(id) VALUES(1);
INSERT INTO cliente(id,cuitCuil,denominacion,contacto_id,domicilio_id,fechaAlta,fechaModificacion,usuario_carga_id,usuario_modificacion_id)
 VALUES(1,'30712345671','Cliente sintetico',1,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,1,1);
INSERT INTO condicion_iva(id,codigoAfip,denominacion,fechaAlta,fechaModificacion,usuario_carga_id,usuario_modificacion_id)
 VALUES(1,6,'Monotributo',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,1,1);
INSERT INTO punto_venta(id,numero,descripcion,fechaAlta,fechaModificacion,usuario_carga_id,usuario_modificacion_id)
 VALUES(1,1,'Punto sintetico',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,1,1);
INSERT INTO tipo_moneda(id,codigoAfip,denominacion,simbolo,fechaAlta,fechaModificacion,usuario_carga_id,usuario_modificacion_id)
 VALUES(1,'PES','Pesos','$',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,1,1);
INSERT INTO articulo(id,codigo,denominacion,fechaAlta,fechaModificacion,usuario_carga_id,usuario_modificacion_id)
 VALUES(1,'TEST','Articulo sintetico',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,1,1);
INSERT INTO lista_precio(id,codigo,denominacion,fechaAlta,fechaModificacion,usuario_carga_id,usuario_modificacion_id)
 VALUES(1,'TEST','Lista sintetica',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,1,1);
INSERT INTO lista_precio_articulo(id,articulo_id,lista_precio_id,precioVenta,fechaAlta,fechaModificacion,usuario_carga_id,usuario_modificacion_id)
 VALUES(1,1,1,200,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,1,1);
INSERT INTO factura_venta(id,numero,fechaEmision,importeCobrado,importeSaldo,importeTotal,cliente_id,condicion_iva_id,moneda_id,punto_venta_id,estado,fechaAlta,fechaModificacion,usuario_carga_id,usuario_modificacion_id)
 VALUES(1,7,CURRENT_TIMESTAMP,0,200,200,1,1,1,1,'PENDIENTE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,1,1),
 (2,8,CURRENT_TIMESTAMP,0,200,200,1,1,1,1,'PENDIENTE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,1,1);
INSERT INTO factura_venta_detalle(id,factura_id,lista_precio_articulo_id,cantidad,precioUnitario,porcentajeBonificacion,importeNeto,importeIva,importeSubtotal)
 VALUES(1,1,1,1,200,0,200,0,200),(2,2,1,1,200,0,200,0,200);
