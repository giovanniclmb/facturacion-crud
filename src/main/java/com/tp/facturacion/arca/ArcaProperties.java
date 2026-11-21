package com.tp.facturacion.arca;

import java.net.URI;
import java.nio.file.Path;
import com.tp.facturacion.model.TipoComprobante;
import org.springframework.boot.context.properties.ConfigurationProperties;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@ConfigurationProperties("arca")
public class ArcaProperties {
    public static final URI WSAA_HOMO = URI.create("https://wsaahomo.afip.gov.ar/ws/services/LoginCms");
    public static final URI WSFE_HOMO = URI.create("https://wswhomo.afip.gov.ar/wsfev1/service.asmx");
    public static final int FACTURA_B = 6;
    public static final int FACTURA_C = 11;

    public enum PerfilEmisor { MONOTRIBUTISTA_O_EXENTO, RESPONSABLE_INSCRIPTO, HOMOLOGACION_B_Y_C }

    private boolean habilitado;
    private PerfilEmisor perfilEmisor = PerfilEmisor.MONOTRIBUTISTA_O_EXENTO;
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
    public static int tipoFiscal(TipoComprobante tipo) {
        if (tipo == TipoComprobante.FACTURA_B) return FACTURA_B;
        return tipo == null || tipo == TipoComprobante.FACTURA_C ? FACTURA_C : 0;
    }
    public boolean admiteTipoFiscal(int tipo) {
        return switch (perfilEmisor) {
            case MONOTRIBUTISTA_O_EXENTO -> tipo == FACTURA_C;
            case RESPONSABLE_INSCRIPTO -> tipo == FACTURA_B;
            case HOMOLOGACION_B_Y_C -> tipo == FACTURA_B || tipo == FACTURA_C;
            case null -> false;
        };
    }
    public int tipoFiscalParaDiagnostico() {
        return perfilEmisor == PerfilEmisor.RESPONSABLE_INSCRIPTO ? FACTURA_B : FACTURA_C;
    }
    public String mensajePerfilIncompatible(int tipo) {
        return tipo == FACTURA_B ? "La Factura B requiere un emisor responsable inscripto"
                : "La Factura C requiere un emisor monotributista o exento";
    }
    public boolean coincideEmisorHomologacion(String ambiente, String emisor) {
        return "HOMOLOGACION".equals(ambiente) && cuit != null && cuit.equals(emisor);
    }
    public String impedimentoAutorizacion(int tipo, Integer puntoVenta, boolean coincideEmisor) {
        if (tipo == 0) return "No aplica. Es un comprobante interno.";
        if (!coincideEmisor) return "El emisor configurado no corresponde al historial fiscal";
        if (!habilitado) return "ARCA está deshabilitada";
        if (!admiteTipoFiscal(tipo)) return mensajePerfilIncompatible(tipo);
        if (puntoVenta == null || puntosVentaExclusivos == null || !puntosVentaExclusivos.contains(puntoVenta)) {
            return "Configure un punto de venta exclusivo para autorizar";
        }
        return null;
    }
    private boolean absoluta(Path ruta) { return ruta != null && ruta.isAbsolute(); }
    @Override public String toString() { return "ArcaProperties[configuración redactada]"; }
}
