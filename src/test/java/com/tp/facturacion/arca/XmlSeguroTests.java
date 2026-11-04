package com.tp.facturacion.arca;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class XmlSeguroTests {
    @Test void rechazaDtdTruncadoYDuplicados() {
        for (String xml : new String[]{"<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///NO-LEER'>]><x>&e;</x>","<x>","<!DOCTYPE x><x/>"})
            assertThrows(ArcaRemotaException.class, () -> XmlSeguro.parsear(xml));
        var raiz=XmlSeguro.parsear("<x xmlns='urn:x'><a/><a/></x>").getDocumentElement();
        assertThrows(ArcaRemotaException.class,()->XmlSeguro.exigirElemento(raiz,"urn:x","a"));
        assertThrows(ArcaRemotaException.class,()->XmlSeguro.exigirElemento(raiz,"otro","a"));
        assertEquals("&lt;&amp;&quot;&apos;&gt;",XmlSeguro.escapar("<&\"'>"));
    }
}
