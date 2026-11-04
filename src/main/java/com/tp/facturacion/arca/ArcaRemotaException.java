package com.tp.facturacion.arca;

/** No conserva cuerpos SOAP ni causas que puedan contener autenticación. */
public class ArcaRemotaException extends RuntimeException {
    public ArcaRemotaException() { super("No se pudo obtener una respuesta válida de ARCA de homologación."); }
    public ArcaRemotaException(String detalleNoPublicable) { this(); }
    @Override public String toString() { return "ArcaRemotaException[detalle redactado]"; }
}
