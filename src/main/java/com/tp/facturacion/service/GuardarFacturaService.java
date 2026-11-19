package com.tp.facturacion.service;

import com.tp.facturacion.arca.ArcaConfiguracionException;
import com.tp.facturacion.arca.ArcaRemotaException;
import com.tp.facturacion.arca.autorizacion.AutorizacionArcaService;
import com.tp.facturacion.dto.FacturaReporteDto;
import com.tp.facturacion.dto.FacturaRequestDto;
import com.tp.facturacion.exception.ReglaNegocioException;
import com.tp.facturacion.model.TipoComprobante;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/** Coordina un guardado local ya confirmado con la solicitud fiscal posterior. */
@Service
public class GuardarFacturaService {
    private final FacturaService facturas;
    private final AutorizacionArcaService arca;

    public GuardarFacturaService(FacturaService facturas, AutorizacionArcaService arca) {
        this.facturas = facturas;
        this.arca = arca;
    }

    public FacturaReporteDto crear(FacturaRequestDto request) {
        validarConfirmacion(request);
        var operacion = request.getOperacionGuardado();
        if (request.getTipoComprobante() != null && operacion == null) {
            throw new IllegalArgumentException("Falta la clave de operación del guardado");
        }
        if (operacion != null) {
            var existente = facturas.buscarOperacion(operacion);
            if (existente.isPresent()) return facturas.obtenerReporte(existente.get());
        }
        FacturaReporteDto guardada;
        try {
            guardada = facturas.crear(request);
        } catch (DataIntegrityViolationException error) {
            if (operacion == null) throw error;
            return facturas.buscarOperacion(operacion).map(facturas::obtenerReporte).orElseThrow(() -> error);
        }
        return solicitarSiCorresponde(guardada, request);
    }

    public FacturaReporteDto actualizar(Long id, FacturaRequestDto request) {
        validarConfirmacion(request);
        return solicitarSiCorresponde(facturas.actualizar(id, request), request);
    }

    private void validarConfirmacion(FacturaRequestDto request) {
        if (request.getTipoComprobante() == TipoComprobante.FACTURA_C && !request.isConfirmarCuitReceptor()) {
            throw new IllegalArgumentException("Confirmá el CUIT del receptor antes de guardar la Factura C");
        }
    }

    private FacturaReporteDto solicitarSiCorresponde(FacturaReporteDto guardada, FacturaRequestDto request) {
        if (request.getTipoComprobante() != TipoComprobante.FACTURA_C) return guardada;

        com.tp.facturacion.dto.FacturaArcaDto resultado = null;
        String mensaje = null;
        boolean verificar = false;
        try {
            resultado = arca.autorizar(guardada.getId(), true, guardada.getRevision());
        } catch (ArcaConfiguracionException | ArcaRemotaException | ReglaNegocioException | IllegalArgumentException error) {
            mensaje = error.getMessage();
        } catch (RuntimeException error) {
            verificar = true;
            mensaje = "No se pudo confirmar el estado fiscal. Consultá ARCA antes de continuar.";
        }
        FacturaReporteDto reporte = guardada;
        try {
            reporte = facturas.obtenerReporte(guardada.getId());
        } catch (RuntimeException error) {
            verificar = true;
            mensaje = "No se pudo recuperar el estado fiscal. Consultá ARCA antes de continuar.";
        }
        boolean terminal = reporte.getEstadoFiscal() == com.tp.facturacion.arca.autorizacion.EstadoIntentoArca.AUTORIZADO
                || reporte.getEstadoFiscal() == com.tp.facturacion.arca.autorizacion.EstadoIntentoArca.RECHAZADO;
        if (terminal) return reporte;
        if (resultado != null && resultado.cae() != null && !resultado.cae().isBlank()) {
            reporte.setCae(resultado.cae());
            reporte.setNumeroFiscal(resultado.numeroFiscal());
            reporte.setPuntoVentaFiscal(resultado.puntoVenta());
            reporte.setCaeVencimiento(resultado.caeVencimiento());
            reporte.setEstadoFiscal(resultado.estadoFiscal());
            reporte.setEstado(FacturaService.ESTADO_EMITIDA);
            reporte.setPuedeImprimir(true);
            bloquearCambios(reporte);
        } else if (verificar || (resultado != null && resultado.puedeConsultar())) {
            if (!reporte.isPuedeConsultar()) {
                reporte.setEstadoFiscal(com.tp.facturacion.arca.autorizacion.EstadoIntentoArca.INCIERTO);
                reporte.setPuedeConsultar(true);
                bloquearCambios(reporte);
            }
        }
        if (mensaje != null) reporte.setMensajeArca(mensaje);
        return reporte;
    }

    private void bloquearCambios(FacturaReporteDto reporte) {
        reporte.setPuedeModificar(false);
        reporte.setPuedeAutorizar(false);
        reporte.setPuedeAnular(false);
        reporte.setPuedeEliminar(false);
    }
}
