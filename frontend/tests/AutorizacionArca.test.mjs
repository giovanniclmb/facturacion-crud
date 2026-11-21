import { test } from 'node:test';
import assert from 'node:assert/strict';
import { montar, factura, pagina, respuesta, flush, diferida } from './appDOM.helpers.mjs';

const borrador = { ...factura, revision: 2, estadoFiscal: null, puedeAutorizar: true, puedeConsultar: false, puedeModificar: true, puedeAnular: true, puedeEliminar: true };
const incierto = { facturaId: 1, numeroLocal: 42, intentoId: 'test', estadoFiscal: 'INCIERTO', numeroFiscal: 1, puntoVenta: 1, tipoComprobante: 11, ambiente: 'HOMOLOGACION', cae: null, caeVencimiento: null, mensaje: 'Resultado pendiente. Consulte ARCA.', errores: [], observaciones: [], puedeAutorizar: false, puedeConsultar: true, puedeModificar: false, puedeAnular: false, puedeEliminar: false };
const preparar = resolver => montar({ resolver: llamada => resolver(llamada) ?? (llamada.url.includes('/facturas?') ? respuesta(pagina([borrador])) : undefined) });
const confirmar = ui => {
    const checkbox = ui.document.querySelector('.arca-confirmacion input');
    assert.ok(checkbox, 'confirmación explícita de CUIT');
    checkbox.checked = true;
    checkbox.dispatchEvent(new ui.window.Event('change', { bubbles: true }));
};

test('doble clic autoriza una vez, anuncia progreso y mantiene bloqueo en incierto', async () => {
    const pendiente = diferida();
    const ui = await preparar(llamada => llamada.url.endsWith('/arca/autorizar') ? pendiente.promise : undefined);
    try {
        assert.equal(ui.llamados.filter(l => l.url.includes('/arca')).length, 0, 'sin N+1 inicial');
        const informar = ui.document.querySelector('.btn-informar-arca');
        assert.ok(informar);
        assert.equal(informar.disabled, true);
        confirmar(ui);
        informar.focus();
        informar.click(); informar.click();
        assert.equal(ui.document.querySelector('.btn-eliminar').disabled, true);
        assert.match(ui.document.querySelector('.arca-estado').textContent, /Informando/);
        pendiente.resolver(respuesta(incierto, 202));
        await flush();
        assert.equal(ui.llamados.filter(l => l.url.endsWith('/arca/autorizar')).length, 1);
        assert.equal(ui.document.querySelector('.btn-consultar-arca').disabled, false);
        assert.equal(ui.document.querySelector('.btn-eliminar').disabled, true);
        assert.match(ui.document.querySelector('.arca-estado').textContent, /INCIERTO/);
        assert.equal(ui.document.activeElement, ui.document.querySelector('.btn-consultar-arca'));
        assert.equal(ui.document.querySelector('.arca-estado').getAttribute('aria-live'), 'polite');
    } finally { ui.cerrar(); }
});

test('tras fallo de red recupera GET, nunca reenvía y conserva bloqueo si GET falla', async () => {
    let recuperar = false;
    const ui = await preparar(llamada => {
        if (llamada.url.endsWith('/arca/autorizar')) throw new TypeError('Sin conexión');
        if (llamada.url.endsWith('/arca')) {
            if (recuperar) return respuesta(incierto);
            throw new TypeError('Sin conexión');
        }
    });
    try {
        confirmar(ui);
        ui.document.querySelector('.btn-informar-arca').click();
        await flush();
        assert.equal(ui.document.querySelector('.btn-eliminar').disabled, true);
        await ui.api.eliminarFactura(borrador);
        assert.equal(ui.confirms, 0, 'handler también bloqueado');
        assert.equal(ui.llamados.filter(l => l.url.endsWith('/arca/autorizar')).length, 1);
        assert.equal(ui.llamados.filter(l => l.url.endsWith('/arca') && l.method === 'GET').length, 1);
        const recargar = ui.document.querySelector('.btn-recargar-arca');
        assert.equal(recargar.hidden, false);
        recuperar = true;
        recargar.click();
        await flush();
        assert.equal(ui.document.querySelector('.btn-consultar-arca').disabled, false);
        assert.equal(ui.document.querySelector('.btn-eliminar').disabled, true);
    } finally { ui.cerrar(); }
});

test('consulta un incierto recargado y muestra autorización fiscal separada del estado comercial', async () => {
    const autorizado = { ...incierto, estadoFiscal: 'AUTORIZADO', cae: '12345678901234', caeVencimiento: '2026-11-13', mensaje: 'Autorizado', puedeConsultar: false };
    const ui = await montar({ resolver: llamada => {
        if (llamada.url.includes('/facturas?')) return respuesta(pagina([{ ...borrador, ...incierto, id: 1 }]));
        if (llamada.url.endsWith('/arca/consultar')) return respuesta(autorizado);
    } });
    try {
        assert.equal(ui.llamados.filter(l => l.url.includes('/arca')).length, 0);
        ui.document.querySelector('.btn-consultar-arca').click();
        await flush();
        const texto = ui.document.querySelector('.estado-factura').textContent;
        assert.match(texto, /Autorizada/);
        assert.match(texto, /AUTORIZADO/);
        assert.match(texto, /12345678901234/);
        assert.match(texto, /2026-11-13/);
        assert.match(texto, /N° fiscal/);
        assert.equal(ui.document.querySelector('.btn-anular').disabled, true);
        assert.equal(ui.llamados.filter(l => l.url.endsWith('/arca/autorizar')).length, 0);
    } finally { ui.cerrar(); }
});

test('rechazo muestra errores como texto y requiere nueva confirmación para corregir y autorizar', async () => {
    const rechazado = { ...incierto, estadoFiscal: 'RECHAZADO', puedeAutorizar: true, puedeConsultar: false, puedeModificar: true, puedeAnular: true, puedeEliminar: true, mensaje: 'Corregir receptor', errores: [{ codigo: 100, mensaje: '<script>alert(1)</script>' }] };
    let envios = 0;
    const ui = await preparar(llamada => {
        if (llamada.url.endsWith('/arca/autorizar')) {
            envios++;
            return respuesta(rechazado);
        }
        if (llamada.url.includes('/facturas?') && envios > 0) {
            return respuesta(pagina([{ ...borrador, revision: 2 + envios, estadoFiscal: 'RECHAZADO',
                mensajeArca: '100: <script>alert(1)</script>' }]));
        }
    });
    try {
        confirmar(ui); ui.document.querySelector('.btn-informar-arca').click();
        await flush();
        assert.equal(ui.document.querySelector('.btn-eliminar').disabled, false);
        assert.equal(ui.document.querySelector('.arca-confirmacion input').checked, false);
        assert.equal(ui.document.querySelector('.btn-informar-arca').disabled, true);
        assert.match(ui.document.querySelector('.arca-estado').textContent, /100: <script>/);
        assert.equal(ui.document.querySelector('.arca-estado script'), null);
        confirmar(ui); ui.document.querySelector('.btn-informar-arca').click();
        await flush();
        const revisiones = ui.llamados.filter(l => l.url.endsWith('/arca/autorizar'))
            .map(l => JSON.parse(l.opciones.body).revision);
        assert.deepEqual(revisiones, [2, 3]);
    } finally { ui.cerrar(); }
});

test('un error de validación se conserva al refrescar el borrador y permite corregirlo', async () => {
    const sinIntento = { ...incierto, estadoFiscal: null, numeroFiscal: null, intentoId: null, mensaje: 'Sin informar', puedeAutorizar: true, puedeConsultar: false, puedeModificar: true, puedeAnular: true, puedeEliminar: true };
    const ui = await preparar(llamada => {
        if (llamada.url.endsWith('/arca/autorizar')) return respuesta({ mensaje: 'CUIT receptor inválido', detalles: [] }, 400);
        if (llamada.url.endsWith('/arca')) return respuesta(sinIntento);
    });
    try {
        confirmar(ui); ui.document.querySelector('.btn-informar-arca').click();
        await flush();
        assert.match(ui.document.querySelector('.arca-estado').textContent, /CUIT receptor inválido/);
        assert.equal(ui.document.querySelector('.btn-eliminar').disabled, false);
        assert.equal(ui.document.querySelector('.arca-confirmacion input').checked, false);
    } finally { ui.cerrar(); }
});

test('recargar listado después de perder la respuesta no habilita mutaciones ni pierde recuperación', async () => {
    const ui = await preparar(llamada => {
        if (llamada.url.includes('/arca')) throw new TypeError('Sin conexión');
    });
    try {
        confirmar(ui); ui.document.querySelector('.btn-informar-arca').click();
        await flush();
        await ui.api.cargarFacturas();
        assert.equal(ui.document.querySelector('.btn-eliminar').disabled, true);
        assert.equal(ui.document.querySelector('.btn-recargar-arca').hidden, false);
        assert.equal(ui.document.querySelector('.btn-recargar-arca').disabled, false);
    } finally { ui.cerrar(); }
});

test('una lectura fiscal incompleta no libera el bloqueo después de perder la respuesta', async () => {
    const ui = await preparar(llamada => {
        if (llamada.url.endsWith('/arca/autorizar')) throw new TypeError('Sin conexión');
        if (llamada.url.endsWith('/arca')) return respuesta({});
    });
    try {
        confirmar(ui); ui.document.querySelector('.btn-informar-arca').click();
        await flush();
        assert.equal(ui.document.querySelector('.btn-eliminar').disabled, true);
        assert.equal(ui.document.querySelector('.btn-recargar-arca').hidden, false);
    } finally { ui.cerrar(); }
});

test('etiqueta número local y número fiscal de forma independiente', async () => {
    const ui = await montar({ resolver: llamada => llamada.url.includes('/facturas?')
        ? respuesta(pagina([{ ...borrador, ...incierto, id: 1 }])) : undefined });
    try {
        assert.match(ui.document.querySelector('.comprobante').textContent, /N° local 42/);
        assert.match(ui.document.querySelector('.arca-estado').textContent, /N° fiscal 1/);
    } finally { ui.cerrar(); }
});

test('un rechazo con historial permite anular, distingue bloqueo de borrado y recupera el motivo por GET', async () => {
    const rechazo = { ...incierto, estadoFiscal: 'RECHAZADO', puedeAutorizar: true, puedeConsultar: false, puedeModificar: true, puedeAnular: true, puedeEliminar: false };
    const ui = await montar({ resolver: llamada => {
        if (llamada.url.includes('/facturas?')) return respuesta(pagina([{ ...borrador, ...rechazo, id: 1 }]));
        if (llamada.url.endsWith('/arca')) return respuesta({ ...rechazo, mensaje: 'CUIT receptor rechazado', errores: [{ codigo: 100, mensaje: 'Revise el receptor' }] });
    } });
    try {
        assert.equal(ui.llamados.filter(l => l.url.includes('/arca')).length, 0);
        assert.equal(ui.document.querySelector('.btn-anular').disabled, false);
        assert.equal(ui.document.querySelector('.btn-eliminar').disabled, true);
        assert.doesNotMatch(ui.document.querySelector('.arca-estado').textContent, /Solo lectura/);
        assert.match(ui.document.querySelector('.arca-estado').textContent, /Historial fiscal: borrado bloqueado/);
        const ver = ui.document.querySelector('.btn-recargar-arca');
        assert.equal(ver.hidden, false);
        assert.match(ver.textContent, /Estado de ARCA/);
        ver.click();
        await flush();
        assert.match(ui.document.querySelector('.arca-estado').textContent, /CUIT receptor rechazado/);
        assert.match(ui.document.querySelector('.arca-estado').textContent, /100: Revise el receptor/);
        assert.equal(ui.llamados.filter(l => l.url.endsWith('/arca') && l.method === 'GET').length, 1);
        assert.equal(ui.llamados.filter(l => l.method === 'POST').length, 0);
    } finally { ui.cerrar(); }
});

test('el listado explica la indisponibilidad de autorización sin consultar cada fila', async () => {
    const ui = await montar({ resolver: llamada => llamada.url.includes('/facturas?')
        ? respuesta(pagina([{ ...borrador, puedeAutorizar: false, mensajeArca: 'ARCA está deshabilitada en esta configuración.' }])) : undefined });
    try {
        assert.match(ui.document.querySelector('.arca-estado').textContent, /ARCA está deshabilitada/);
        assert.equal(ui.llamados.filter(l => l.url.includes('/arca')).length, 0);
    } finally { ui.cerrar(); }
});

test('Factura B confirma consumidor final y muestra su número fiscal al solicitar CAE', async () => {
    const fiscal = { ...borrador, tipoComprobante: 'FACTURA_B', clienteDenominacion: 'Consumidor Final' };
    const ui = await montar({ resolver: llamada => {
        if (llamada.url.includes('/facturas?')) return respuesta(pagina([fiscal]));
        if (llamada.url.endsWith('/1/detalle')) return respuesta({
            revision: fiscal.revision, tipoComprobante: 'FACTURA_B',
            documentoReceptorTipo: null, documentoReceptorNumero: null
        });
        if (llamada.url.endsWith('/arca/autorizar')) return respuesta({
            ...incierto, tipoComprobante: 6, estadoFiscal: 'AUTORIZADO', cae: '12345678901234', numeroFiscal: 7
        });
    } });
    try {
        const fila = ui.document.querySelector('#facturas-tbody tr');
        const etiqueta = fila.querySelector('.arca-confirmacion');
        assert.match(etiqueta.textContent, /Confirmo los datos del consumidor final/);
        assert.equal(fila.querySelector('.btn-informar-arca').disabled, true);
        confirmar(ui);
        fila.querySelector('.btn-informar-arca').click();
        await flush();
        assert.match(fila.querySelector('strong').textContent, /Factura B 00001-00000007/);
    } finally { ui.cerrar(); }
});

test('la Factura B identificada muestra el documento vigente y permite cancelar el envío', async t => {
    const fiscal = { ...borrador, tipoComprobante: 'FACTURA_B' };
    const ui = await montar({ resolver: llamada => {
        if (llamada.url.includes('/facturas?')) return respuesta(pagina([fiscal]));
        if (llamada.url.endsWith('/1/detalle')) return respuesta({
            revision: fiscal.revision, tipoComprobante: 'FACTURA_B',
            documentoReceptorTipo: 96, documentoReceptorNumero: 12345678
        });
    } });
    t.after(() => ui.cerrar());
    const original = globalThis.confirm;
    t.after(() => { globalThis.confirm = original; });
    let pregunta;
    globalThis.confirm = texto => { pregunta = texto; return false; };
    confirmar(ui);
    ui.document.querySelector('.btn-informar-arca').click();
    await flush();
    assert.match(pregunta, /DNI 12345678/);
    assert.equal(ui.llamados.some(llamada => llamada.url.endsWith('/arca/autorizar')), false);
    assert.equal(ui.document.querySelector('.arca-confirmacion input').checked, false);
    assert.equal(ui.document.querySelector('.btn-eliminar').disabled, false);
});

test('una Factura B editada en otra pestaña actualiza el listado antes de pedir confirmación', async t => {
    const fiscal = { ...borrador, tipoComprobante: 'FACTURA_B' };
    const ui = await montar({ resolver: llamada => {
        if (llamada.url.includes('/facturas?')) return respuesta(pagina([fiscal]));
        if (llamada.url.endsWith('/1/detalle')) return respuesta({
            revision: fiscal.revision + 1, tipoComprobante: 'FACTURA_B',
            documentoReceptorTipo: 96, documentoReceptorNumero: 12345678
        });
    } });
    t.after(() => ui.cerrar());
    confirmar(ui);
    ui.document.querySelector('.btn-informar-arca').click();
    await flush();
    assert.equal(ui.confirms, 0);
    assert.equal(ui.llamados.some(llamada => llamada.url.endsWith('/arca/autorizar')), false);
    assert.equal(ui.llamados.filter(llamada => llamada.url.includes('/facturas?')).length, 2);
});
