package com.tp.facturacion.arca.autorizacion;

import com.tp.facturacion.arca.ArcaProperties;
import com.tp.facturacion.arca.WsaaClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import static com.tp.facturacion.arca.autorizacion.ContratoFiscal.Comprobante;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "spring.jpa.show-sql=false")
@EnabledIfEnvironmentVariable(named = "ARCA_VERIFICAR_FACTURA_ID", matches = "[1-9][0-9]*")
class ConsultaHomologacionManualTests {
    @Autowired IntentoArcaRepository intentos;
    @Autowired TransactionTemplate transacciones;
    @Autowired ArcaProperties configuracion;
    @Autowired WsaaClient wsaa;
    @Autowired WsfeAutorizacionClient wsfe;

    @Test
    void compruebaElCaePersistidoMedianteUnaConsultaRemota() {
        long facturaId = Long.parseLong(System.getenv("ARCA_VERIFICAR_FACTURA_ID"));
        Comprobante guardado = transacciones.execute(estado -> {
            var intento = intentos.findFirstByFacturaIdOrderByCreadoDescIdDesc(facturaId).orElseThrow();
            assertEquals(EstadoIntentoArca.AUTORIZADO, intento.getEstado());
            assertEquals("HOMOLOGACION", intento.getSerie().getAmbiente());
            assertTrue(configuracion.getCuit().equals(intento.getSerie().getEmisor()));
            assertTrue(intento.getCae().equals(intento.getFactura().getCae()));
            return new Comprobante(intento.getSerie().serie(), intento.getNumero(), intento.getSnapshot(),
                    intento.getCae(), intento.getVencimiento());
        });

        var consulta = wsfe.consultar(wsaa.obtenerTicket(), guardado.serie(), guardado.numero());
        var remoto = consulta.comprobante().orElseThrow();
        assertTrue(guardado.serie().equals(remoto.serie()));
        assertEquals(guardado.numero(), remoto.numero());
        assertTrue(AlmacenIntentosArca.coincide(guardado.snapshot(), remoto.snapshot()));
        assertTrue(guardado.cae().equals(remoto.cae()));
        assertEquals(guardado.vencimiento(), remoto.vencimiento());
    }
}
