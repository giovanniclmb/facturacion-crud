package com.tp.facturacion.service;

import com.tp.facturacion.arca.autorizacion.AutorizacionArcaService;
import com.tp.facturacion.arca.autorizacion.EstadoIntentoArca;
import com.tp.facturacion.dto.FacturaArcaDto;
import com.tp.facturacion.dto.FacturaReporteDto;
import com.tp.facturacion.dto.FacturaRequestDto;
import com.tp.facturacion.model.TipoComprobante;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GuardarFacturaServiceTests {
    private final FacturaService facturas = mock(FacturaService.class);
    private final AutorizacionArcaService arca = mock(AutorizacionArcaService.class);
    private final GuardarFacturaService servicio = new GuardarFacturaService(facturas, arca);

    @Test
    void conservaElIdYBloqueaSiFallaLaLecturaPosteriorAlDespacho() {
        var request = prepararAlta();
        when(arca.autorizar(73L, true, 0L)).thenThrow(new IllegalStateException("Falla de persistencia"));
        when(facturas.obtenerReporte(73L)).thenThrow(new IllegalStateException("Falla de lectura"));

        var respuesta = servicio.crear(request);

        assertEquals(73L, respuesta.getId());
        assertEquals(EstadoIntentoArca.INCIERTO, respuesta.getEstadoFiscal());
        assertTrue(respuesta.isPuedeConsultar());
        assertFalse(respuesta.isPuedeModificar());
        assertFalse(respuesta.isPuedeAutorizar());
    }

    @Test
    void prevaleceUnaAutorizacionConfirmadaConcurrentemente() {
        var request = prepararAlta();
        var autorizada = new FacturaReporteDto();
        autorizada.setId(73L);
        autorizada.setEstadoFiscal(EstadoIntentoArca.AUTORIZADO);
        autorizada.setCae("12345678901234");
        when(facturas.obtenerReporte(73L)).thenReturn(autorizada);
        when(arca.autorizar(73L, true, 0L)).thenReturn(new FacturaArcaDto(73, 1L, UUID.randomUUID(),
                EstadoIntentoArca.INCIERTO, 1L, 99001, 11, "HOMOLOGACION", null, null,
                "Pendiente", List.of(), List.of(), false, true, false, false, false));

        assertEquals(EstadoIntentoArca.AUTORIZADO, servicio.crear(request).getEstadoFiscal());
    }

    private FacturaRequestDto prepararAlta() {
        var request = new FacturaRequestDto();
        request.setTipoComprobante(TipoComprobante.FACTURA_C);
        request.setConfirmarCuitReceptor(true);
        request.setOperacionGuardado(UUID.randomUUID());
        var guardada = new FacturaReporteDto();
        guardada.setId(73L);
        guardada.setRevision(0L);
        when(facturas.crear(request)).thenReturn(guardada);
        return request;
    }
}
