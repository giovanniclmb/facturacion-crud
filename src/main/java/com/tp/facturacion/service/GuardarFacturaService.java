package com.tp.facturacion.service;

import com.tp.facturacion.dto.FacturaReporteDto;
import com.tp.facturacion.dto.FacturaRequestDto;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/** Guarda el comprobante local y conserva la idempotencia del alta. */
@Service
public class GuardarFacturaService {
    private final FacturaService facturas;

    public GuardarFacturaService(FacturaService facturas) {
        this.facturas = facturas;
    }

    public FacturaReporteDto crear(FacturaRequestDto request) {
        var operacion = request.getOperacionGuardado();
        if (request.getTipoComprobante() != null && operacion == null) {
            throw new IllegalArgumentException("Falta la clave de operación del guardado");
        }
        if (operacion != null) {
            var existente = facturas.buscarOperacion(operacion);
            if (existente.isPresent()) return facturas.obtenerReporte(existente.get());
        }
        try {
            return facturas.crear(request);
        } catch (DataIntegrityViolationException error) {
            if (operacion == null) throw error;
            return facturas.buscarOperacion(operacion).map(facturas::obtenerReporte).orElseThrow(() -> error);
        }
    }

    public FacturaReporteDto actualizar(Long id, FacturaRequestDto request) {
        return facturas.actualizar(id, request);
    }
}
