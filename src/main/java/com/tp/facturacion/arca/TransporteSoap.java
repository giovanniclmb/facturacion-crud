package com.tp.facturacion.arca;
import java.net.URI;
@FunctionalInterface
public interface TransporteSoap { RespuestaSoap enviar(URI destino, String accion, String sobre); }
