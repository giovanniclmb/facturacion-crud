import test from 'node:test';
import assert from 'node:assert/strict';
import { montar, flush, diferida, respuesta, opcion, factura, pagina, precio } from './appDOM.helpers.mjs';

const porId = (app, id) => app.document.getElementById(id);
const submit = (app, id) => porId(app, id).dispatchEvent(new app.window.Event('submit', { bubbles: true, cancelable: true }));

test('arranque inicializa borrador y pide listado mientras catálogos siguen pendientes', async t => {
    const gate = diferida();
    const app = await montar({ resolver: r => r.url.includes('/catalogos/') ? gate.promise : undefined });
    t.after(() => app.cerrar());
    assert.equal(porId(app, 'items-tbody').rows.length, 1);
    assert.equal(app.llamados.filter(r => r.url.includes('/facturas')).length, 1);
    assert.match(porId(app, 'facturas-tbody').textContent, /42/);
    assert.equal(porId(app, 'alta-campos').disabled, true);
    assert.match(porId(app, 'catalogos-estado').textContent, /cargando/i);
    porId(app, 'alta-observaciones').value = 'Borrador conservado';
    gate.resolver(respuesta(opcion));
    await flush();
    assert.equal(porId(app, 'alta-campos').disabled, false);
    assert.equal(porId(app, 'alta-observaciones').value, 'Borrador conservado');
    assert.equal(app.llamados.filter(r => r.url.includes('/facturas')).length, 1);
});

test('catálogos fallan y reintentan sin borrar listado ni borrador', async t => {
    let falla = true;
    const app = await montar({ resolver: r => r.url.includes('/catalogos/') && falla ? respuesta({ mensaje: 'Catálogo temporalmente caído' }, 503) : undefined });
    t.after(() => app.cerrar());
    assert.equal(porId(app, 'facturas-tbody').rows.length, 1);
    assert.equal(porId(app, 'alta-campos').disabled, true);
    assert.equal(porId(app, 'btn-reintentar-catalogos').hidden, false);
    assert.match(porId(app, 'catalogos-estado').textContent, /catálogo/i);
    porId(app, 'alta-observaciones').value = 'No perder';
    falla = false;
    porId(app, 'btn-reintentar-catalogos').click();
    await flush();
    assert.equal(porId(app, 'alta-campos').disabled, false);
    assert.equal(porId(app, 'alta-observaciones').value, 'No perder');
    assert.equal(app.llamados.filter(r => r.url.includes('/facturas')).length, 1);
});

test('fallo del listado no bloquea catálogos y alta no se envía antes de tenerlos', async t => {
    const gate = diferida();
    const app = await montar({ resolver: r => r.url.includes('/catalogos/') ? gate.promise : respuesta({ mensaje: 'Listado caído' }, 503) });
    t.after(() => app.cerrar());
    submit(app, 'form-alta');
    await flush();
    assert.equal(app.llamados.some(r => r.method === 'POST'), false);
    gate.resolver(respuesta(opcion));
    await flush();
    assert.equal(porId(app, 'alta-campos').disabled, false);
    assert.match(porId(app, 'listado-resumen').textContent, /no se pudo/i);
});

test('cantidades válidas y añadir/quitar conservan nodos, valores y foco', async t => {
    const app = await montar();
    t.after(() => app.cerrar());
    const cantidad = app.document.querySelector('[aria-label="Cantidad del ítem 1"]');
    assert.equal(cantidad.min, '0.01');
    assert.equal(cantidad.step, '0.01');
    assert.equal(cantidad.validity.valid, true);
    cantidad.value = '2.5';
    cantidad.dispatchEvent(new app.window.Event('input'));
    cantidad.focus();
    porId(app, 'btn-agregar-item').click();
    assert.equal(app.document.querySelector('[aria-label="Cantidad del ítem 1"]'), cantidad);
    assert.equal(app.document.activeElement, cantidad);
    assert.equal(cantidad.value, '2.5');
    app.document.querySelector('[aria-label="Quitar ítem 2"]').click();
    assert.equal(app.document.activeElement, cantidad);
    assert.equal(cantidad.value, '2.5');
    assert.equal(app.document.querySelectorAll('.btn-sm,.form-control-sm').length, 0);
});

test('render de ítems calcula subtotales y total una sola vez global; edición actualiza importes', async t => {
    const app = await montar();
    t.after(() => app.cerrar());
    app.api.setItems(Array.from({ length: 50 }, () => ({ precio, cantidad: 2, porcentajeBonificacion: 10 })));
    app.metricas.subtotalDe = app.metricas.actualizarTotal = 0;
    app.api.renderizarItems();
    assert.equal(app.metricas.actualizarTotal, 1);
    assert.equal(app.metricas.subtotalDe, 100);
    assert.match(porId(app, 'items-total').textContent, /9[.]000,00/);
    const cantidad = app.document.querySelector('[aria-label="Cantidad del ítem 1"]');
    cantidad.value = '3';
    cantidad.dispatchEvent(new app.window.Event('input'));
    assert.match(porId(app, 'items-total').textContent, /9[.]090,00/);
    assert.match(porId(app, 'items-tbody').rows[0].cells[4].textContent, /270,00/);
});

test('recarga bloquea acciones también en handlers antes de diálogos y las recupera al terminar', async t => {
    let gate;
    const app = await montar({ resolver: r => gate && r.url.includes('/facturas') ? gate.promise : undefined });
    t.after(() => app.cerrar());
    gate = diferida();
    const carga = app.api.cargarFacturas();
    assert.equal(app.document.querySelector('.acciones button:first-child').disabled, true);
    assert.equal(app.document.querySelector('.acciones button:nth-child(2)').disabled, true);
    await app.api.anularFactura(factura);
    await app.api.eliminarFactura(factura);
    assert.equal(app.prompts, 0);
    assert.equal(app.confirms, 0);
    gate.resolver(respuesta(pagina([factura])));
    await carga;
    assert.equal(app.document.querySelector('.acciones button:first-child').disabled, false);
});

for (const accion of ['anular', 'eliminar']) {
    test(`${accion}: evita solicitudes duplicadas y reactiva la fila tras error`, async t => {
        const gate = diferida();
        const app = await montar({ resolver: r => r.method !== 'GET' ? gate.promise : undefined });
        t.after(() => app.cerrar());
        const ejecutar = accion === 'anular' ? app.api.anularFactura : app.api.eliminarFactura;
        const solicitud = ejecutar(factura);
        const duplicada = ejecutar(factura);
        await flush();
        const solicitudes = app.llamados.filter(r => r.method !== 'GET').length;
        const dialogos = app.prompts + app.confirms;
        const bloqueadas = [...app.document.querySelectorAll('.acciones button')].every(b => b.disabled);
        gate.resolver(respuesta({ mensaje: 'Operación fallida' }, 503));
        await Promise.all([solicitud, duplicada]);
        assert.equal(solicitudes, 1);
        assert.equal(dialogos, 1);
        assert.equal(bloqueadas, true);
        assert.equal(app.document.querySelector('.acciones button:first-child').disabled, false);
        assert.equal(app.document.querySelector('.acciones button:nth-child(2)').disabled, false);
    });
}

test('CAE conserva acciones bloqueadas, cinco celdas guardan datos completos y etiquetas móviles', async t => {
    const conCae = { ...factura, cae: '12345678901234567890', estado: 'EMITIDA' };
    const app = await montar({ resolver: r => r.url.includes('/facturas') ? respuesta(pagina([conCae])) : undefined });
    t.after(() => app.cerrar());
    const fila = porId(app, 'facturas-tbody').rows[0];
    assert.match(porId(app, 'listado-resumen').textContent, /de 1 comprobante ·/);
    assert.equal(fila.cells.length, 5);
    assert.deepEqual([...fila.cells].map(c => c.dataset.label), ['Comprobante', 'Cliente', 'Importe', 'Estado y ARCA', 'Acciones']);
    for (const dato of ['42', '30/10/2026', 'Ana', 'Monotributo', 'Local principal', '2', '200,00', 'EMITIDA', conCae.cae, 'Solo lectura']) assert.ok(fila.textContent.includes(dato), dato);
    assert.equal(app.document.querySelector('.acciones button:first-child').disabled, true);
    assert.equal(app.document.querySelector('.acciones button:nth-child(2)').disabled, true);
    assert.match(app.document.querySelector('.acciones button:nth-child(2)').getAttribute('aria-label'), /42/);
});

test('mensajes respetan reducción de movimiento', async t => {
    const app = await montar({ reducedMotion: true, resolver: r => r.url.includes('/facturas') ? respuesta({ mensaje: 'Error de prueba' }, 503) : undefined });
    t.after(() => app.cerrar());
    assert.ok(app.scrolls.length > 0);
    assert.equal(app.scrolls.at(-1).behavior, 'instant');
});

test('acciones distinguen facturas con el mismo número en diferentes puntos de venta', async t => {
    const otra = { ...factura, id: 2, cantidadItems: 1, puntoVentaDescripcion: 'Sucursal centro' };
    const app = await montar({ resolver: r => r.url.includes('/facturas') ? respuesta(pagina([factura, otra])) : undefined });
    t.after(() => app.cerrar());
    assert.match(porId(app, 'facturas-tbody').rows[1].querySelector('.numero .fila-secundaria').textContent, /^1 ítem$/);
    for (const selector of ['.btn-anular', '.btn-eliminar']) {
        const etiquetas = [...app.document.querySelectorAll(selector)].map(b => b.getAttribute('aria-label'));
        assert.equal(new Set(etiquetas).size, 2);
        assert.ok(etiquetas[0].includes('Local principal'));
        assert.ok(etiquetas[1].includes('Sucursal centro'));
    }
});
