package com.tp.facturacion.arca.autorizacion;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.util.UUID;
class PersistenciaArcaTests {
 @Test void estadosFinalesNoBloqueanNuevoIntentoPeroAutorizadoSi() {
  assertTrue(IntentoArca.ESTADOS_ACTIVOS.contains(EstadoIntentoArca.AUTORIZADO));
  assertFalse(IntentoArca.ESTADOS_ACTIVOS.contains(EstadoIntentoArca.RECHAZADO));
  assertFalse(IntentoArca.ESTADOS_ACTIVOS.contains(EstadoIntentoArca.FALLIDO_ANTES_ENVIO));
 }
 @Test void noExponeDocumentoNiSnapshotEnToString() {
  assertFalse(new IntentoArca().toString().contains("docNumero"));
 }
}
