package com.tp.facturacion.arca;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TicketAccesoTests {
    @Test void margenDiezMinutosEstrictoYRepresentacionRedactada() {
        Clock reloj=Clock.fixed(CredencialesPrueba.AHORA,ZoneOffset.UTC);
        assertFalse(new TicketAcceso("TOKEN-SECRETO","SIGN-SECRETO",reloj.instant().plusSeconds(600)).vigente(reloj));
        assertTrue(new TicketAcceso("TOKEN-SECRETO","SIGN-SECRETO",reloj.instant().plusSeconds(601)).vigente(reloj));
        assertFalse(new TicketAcceso("TOKEN-SECRETO","SIGN-SECRETO",reloj.instant()).toString().contains("SECRETO"));
        assertThrows(ArcaRemotaException.class,()->new TicketAcceso(" ","sign",reloj.instant()));
        assertThrows(ArcaRemotaException.class,()->new TicketAcceso("token","",reloj.instant()));
    }
}
