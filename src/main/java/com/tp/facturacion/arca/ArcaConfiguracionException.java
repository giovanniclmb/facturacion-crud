package com.tp.facturacion.arca;

/** Los detalles de archivos/credenciales nunca forman parte de la excepción pública. */
public class ArcaConfiguracionException extends RuntimeException {
    public ArcaConfiguracionException() { super("ARCA no está disponible: revise la habilitación y la configuración local."); }
    public ArcaConfiguracionException(String detalleNoPublicable) { this(); }
    @Override public String toString() { return "ArcaConfiguracionException[detalle redactado]"; }
}
