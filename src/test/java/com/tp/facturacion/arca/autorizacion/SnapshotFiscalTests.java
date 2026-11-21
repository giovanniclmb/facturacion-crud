package com.tp.facturacion.arca.autorizacion;

import com.tp.facturacion.model.*;
import java.time.*;
import java.util.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class SnapshotFiscalTests {
    static final String EMISOR="20111111112", RECEPTOR="20222222223";
    final SnapshotFiscalFactory factory=new SnapshotFiscalFactory(Clock.fixed(Instant.parse("2026-11-03T12:00:00Z"),ZoneOffset.UTC));
    static FacturaVenta facturaValida() {
        var f=new FacturaVenta(); f.setEstado("PENDIENTE"); f.setFechaEmision(Date.from(Instant.parse("2026-11-03T12:00:00Z")));
        var c=new Cliente();c.setCuitCuil(RECEPTOR);f.setCliente(c);
        var iva=new CondicionIva();iva.setCodigoAfip(5);f.setCondicionIva(iva);
        var m=new TipoMoneda();m.setCodigoAfip("PES");f.setMoneda(m);
        var p=new PuntoVenta();p.setNumero(1);f.setPuntoVenta(p);
        var d=new FacturaVentaDetalle();d.setCantidad(1);d.setPrecioUnitario(100.005);d.setImporteNeto(100.005);d.setImporteSubtotal(100.005);f.addDetalle(d);f.setImporteTotal(100.005);return f;
    }
    @Test void requiereConfirmacionExplicita() {assertThrows(IllegalArgumentException.class,()->factory.crear(facturaValida(),EMISOR,Set.of(5),false));}
    @Test void normalizaValoresGuardadosYHashCanonico() {
        var s=factory.crear(facturaValida(),EMISOR,Set.of(5),true); assertEquals(new BigDecimal("100.01"),s.total());
        assertEquals(20222222223L,s.docNumero()); assertEquals(64,factory.hash(s).length());
        var equivalente=new ContratoFiscal.Snapshot(1,80,s.docNumero(),5,s.fecha(),"PES",new BigDecimal("1.000000"),new BigDecimal("100.0100"));
        assertEquals(factory.hash(s),factory.hash(equivalente));
    }
    @Test void bSoloIdentificaAlConsumidorCuandoElTotalLlegaAlUmbral() {
        var b = facturaB(9_999_999.99);
        var anonima = factory.crear(b, EMISOR, Set.of(5), true);
        assertEquals(99, anonima.docTipo());
        assertEquals(0, anonima.docNumero());
        assertEquals(5, anonima.condicionIva());
        assertEquals(anonima.total(), anonima.neto().add(anonima.iva()));

        var enUmbral = facturaB(10_000_000.00);
        assertThrows(IllegalArgumentException.class, () -> factory.crear(enUmbral, EMISOR, Set.of(5), true));
        enUmbral.setDocumentoReceptorTipo(96);
        enUmbral.setDocumentoReceptorNumero(22_222_222L);
        var identificada = factory.crear(enUmbral, EMISOR, Set.of(5), true);
        assertEquals(96, identificada.docTipo());
        assertEquals(22_222_222, identificada.docNumero());
    }
    @Test void bExigeIvaExplicitoYNoPermiteUsarElCuitDelEmisor() {
        var b = facturaB(121);
        b.getDetalles().getFirst().setIvaPorcentaje(null);
        assertThrows(IllegalArgumentException.class, () -> factory.crear(b, EMISOR, Set.of(5), true));
        b.getDetalles().getFirst().setIvaPorcentaje(BigDecimal.valueOf(21));
        b.setDocumentoReceptorTipo(86);
        b.setDocumentoReceptorNumero(Long.valueOf(EMISOR));
        assertThrows(IllegalArgumentException.class, () -> factory.crear(b, EMISOR, Set.of(5), true));
    }
    private FacturaVenta facturaB(double total) {
        var b = facturaValida();
        b.setTipoComprobante(TipoComprobante.FACTURA_B);
        b.setCliente(null);
        b.setImporteTotal(total);
        var detalle = b.getDetalles().getFirst();
        var finalCobrado = BigDecimal.valueOf(total);
        var neto = SnapshotFiscalFactory.netoFacturaB(finalCobrado);
        detalle.setPrecioUnitario(total);
        detalle.setImporteSubtotal(total);
        detalle.setImporteNeto(neto.doubleValue());
        detalle.setImporteIva(finalCobrado.subtract(neto).doubleValue());
        detalle.setIvaPorcentaje(BigDecimal.valueOf(21));
        return b;
    }
    @ParameterizedTest @ValueSource(strings={"20222222224","20111111112","20-22222222-3","", "27222222223"})
    void rechazaDocumentoInvalidoOEmisor(String doc) {var f=facturaValida();f.getCliente().setCuitCuil(doc);assertThrows(IllegalArgumentException.class,()->factory.crear(f,EMISOR,Set.of(5),true));}
    @Test void validaCatalogoMonedaFechaEstadoYDetalles() {
        var inicial=facturaValida();assertThrows(IllegalArgumentException.class,()->factory.crear(inicial,EMISOR,Set.of(1),true));
        inicial.getMoneda().setCodigoAfip("DOL");assertThrows(IllegalArgumentException.class,()->factory.crear(inicial,EMISOR,Set.of(5),true));
        var f=facturaValida(); f.setFechaEmision(Date.from(Instant.parse("2026-10-31T12:00:00Z")));var mes=f;assertThrows(IllegalArgumentException.class,()->factory.crear(mes,EMISOR,Set.of(5),true));
        f=facturaValida();f.setFechaEmision(Date.from(Instant.parse("2026-11-09T12:00:00Z")));var fuera=f;assertThrows(IllegalArgumentException.class,()->factory.crear(fuera,EMISOR,Set.of(5),true));
        f=facturaValida();f.setImporteTotal(99);var suma=f;assertThrows(IllegalArgumentException.class,()->factory.crear(suma,EMISOR,Set.of(5),true));
        f=facturaValida();f.getDetalles().clear();var vacia=f;assertThrows(IllegalArgumentException.class,()->factory.crear(vacia,EMISOR,Set.of(5),true));
    }
    @ParameterizedTest @ValueSource(doubles={Double.NaN,Double.POSITIVE_INFINITY,-1,0})
    void rechazaImportesNoFinitosONoPositivos(double n) {var f=facturaValida();f.setImporteTotal(n);f.getDetalles().getFirst().setImporteSubtotal(n);assertThrows(IllegalArgumentException.class,()->factory.crear(f,EMISOR,Set.of(5),true));}
    @Test void netoGuardadoDebeCoincidirConSubtotalSinIva() {
        var f=facturaValida();f.getDetalles().getFirst().setImporteNeto(90);
        assertThrows(IllegalArgumentException.class,()->factory.crear(f,EMISOR,Set.of(5),true));
    }
    @Test void noAceptaDocumentoDeCerosAunqueTengaChecksumCero() {
        var f=facturaValida();f.getCliente().setCuitCuil("00000000000");
        assertThrows(IllegalArgumentException.class,()->factory.crear(f,EMISOR,Set.of(5),true));
    }
    @Test void usaFechaArgentinaYVentanaInclusivaDeCincoDias() {
        var reloj=Clock.fixed(Instant.parse("2026-11-04T01:00:00Z"),ZoneOffset.UTC);
        var f=facturaValida();f.setFechaEmision(Date.from(Instant.parse("2026-11-08T12:00:00Z")));
        assertEquals(LocalDate.of(2026,11,8),new SnapshotFiscalFactory(reloj).crear(f,EMISOR,Set.of(5),true).fecha());
    }
    @ParameterizedTest @ValueSource(doubles={Double.NaN,Double.NEGATIVE_INFINITY,-0.01})
    void rechazaValoresGuardadosInvalidosDelDetalle(double n) {
        var f=facturaValida();f.getDetalles().getFirst().setPrecioUnitario(n);
        assertThrows(IllegalArgumentException.class,()->factory.crear(f,EMISOR,Set.of(5),true));
    }
}
