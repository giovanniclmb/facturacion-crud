import { test } from 'node:test';
import assert from 'node:assert/strict';

import { permisosFactura } from '../public/js/models/permisosFactura.js';

test('una factura con CAE bloquea anular y eliminar, independientemente del estado', () => {
    for (const estado of ['PENDIENTE', 'EMITIDA', 'ANULADA']) {
        const permisos = permisosFactura({ estado, cae: ' 12345678901234 ' });
        assert.equal(permisos.anular, false);
        assert.equal(permisos.eliminar, false);
        assert.ok(permisos.motivo.includes('ARCA'));
    }
});

test('CAE nulo, vacío o espacios conserva las acciones de una factura sin autorizar', () => {
    for (const cae of [null, '', '   ', '\t\n']) {
        const permisos = permisosFactura({ estado: 'PENDIENTE', cae });
        assert.equal(permisos.anular, true);
        assert.equal(permisos.eliminar, true);
    }
});

test('una anulada sin CAE puede eliminarse pero no volver a anularse', () => {
    const permisos = permisosFactura({ estado: 'ANULADA', cae: null });
    assert.equal(permisos.anular, false);
    assert.equal(permisos.eliminar, true);
});

test('intentos activos y autorización bloquean mutaciones incluso sin CAE', () => {
    for (const estadoFiscal of ['PREPARANDO', 'ENVIANDO', 'INCIERTO', 'AUTORIZADO']) {
        const permisos = permisosFactura({ estado: 'PENDIENTE', cae: null, estadoFiscal });
        assert.equal(permisos.modificar, false);
        assert.equal(permisos.anular, false);
        assert.equal(permisos.eliminar, false);
    }
});

test('respeta permisos del backend y un rechazo permite corrección', () => {
    assert.equal(permisosFactura({ estado: 'PENDIENTE', cae: null, puedeEliminar: false }).eliminar, false);
    const p = permisosFactura({ estado: 'PENDIENTE', cae: null, estadoFiscal: 'RECHAZADO', puedeModificar: true, puedeAutorizar: true });
    assert.equal(p.modificar, true);
    assert.equal(p.autorizar, true);
    assert.equal(permisosFactura({ estado: 'ANULADA', cae: null, puedeAutorizar: true }).autorizar, false);
});
