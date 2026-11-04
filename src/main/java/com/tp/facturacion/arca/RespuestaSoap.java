package com.tp.facturacion.arca;
public record RespuestaSoap(int status, byte[] cuerpo) {
    @Override public String toString() { return "RespuestaSoap[contenido redactado]"; }
}
