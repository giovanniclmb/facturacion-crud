package com.tp.facturacion.service;

import com.tp.facturacion.dto.FacturaReporteDto;
import com.tp.facturacion.dto.FacturaRequestDto;
import com.tp.facturacion.model.TipoComprobante;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GuardarFacturaServiceTests {
    private final FacturaService facturas = mock(FacturaService.class);
    private final GuardarFacturaService servicio = new GuardarFacturaService(facturas);

    @Test
    void guardarFacturaCDejaPendienteSinConsultarArca() {
        var request = prepararAlta();
        var guardada = servicio.crear(request);

        assertEquals(73L, guardada.getId());
        assertNull(guardada.getCae());
        verify(facturas).buscarOperacion(request.getOperacionGuardado());
        verify(facturas).crear(request);
        verifyNoMoreInteractions(facturas);
    }

    @Test
    void editarFacturaCDejaPendienteSinConsultarArca() {
        var request = new FacturaRequestDto();
        request.setTipoComprobante(TipoComprobante.FACTURA_C);
        var guardada = new FacturaReporteDto();
        guardada.setId(73L);
        when(facturas.actualizar(73L, request)).thenReturn(guardada);

        assertSame(guardada, servicio.actualizar(73L, request));
        verify(facturas).actualizar(73L, request);
        verifyNoMoreInteractions(facturas);
    }

    private FacturaRequestDto prepararAlta() {
        var request = new FacturaRequestDto();
        request.setTipoComprobante(TipoComprobante.FACTURA_C);
        request.setOperacionGuardado(UUID.randomUUID());
        var guardada = new FacturaReporteDto();
        guardada.setId(73L);
        guardada.setRevision(0L);
        when(facturas.crear(request)).thenReturn(guardada);
        return request;
    }
}
