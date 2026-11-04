package com.tp.facturacion.arca;

import java.net.URI;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@ConfigurationProperties("arca")
public class ArcaProperties {
    public static final URI WSAA_HOMO = URI.create("https://wsaahomo.afip.gov.ar/ws/services/LoginCms");
    public static final URI WSFE_HOMO = URI.create("https://wswhomo.afip.gov.ar/wsfev1/service.asmx");
    private boolean habilitado;
    // Declaración operativa: WSFE no ofrece una clave de correlación de cliente.
    private java.util.Set<Integer> puntosVentaExclusivos = java.util.Set.of();
    private String cuit;
    private Path certificado;
    private Path clavePrivada;
    private Path ticketCache;
    private URI wsaaUrl = WSAA_HOMO;
    private URI wsfeUrl = WSFE_HOMO;

    public void exigirHabilitada() {
        if (!habilitado) throw new ArcaConfiguracionException();
    }
    public void validarConfiguracion() {
        exigirHabilitada();
        if (cuit == null || !cuit.matches("[0-9]{11}") || !absoluta(certificado)
                || !absoluta(clavePrivada) || !absoluta(ticketCache)
                || !WSAA_HOMO.equals(wsaaUrl) || !WSFE_HOMO.equals(wsfeUrl)) {
            throw new ArcaConfiguracionException();
        }
    }
    public void validarAutorizacion(int puntoVenta) {
        validarConfiguracion();
        if (puntosVentaExclusivos == null || !puntosVentaExclusivos.contains(puntoVenta))
            throw new ArcaConfiguracionException();
    }
    private boolean absoluta(Path ruta) { return ruta != null && ruta.isAbsolute(); }
    @Override public String toString() { return "ArcaProperties[configuración redactada]"; }
}
