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
    assert.equal(app.document.querySelector('.btn-anular').disabled, true);
    assert.equal(app.document.querySelector('.btn-eliminar').disabled, true);
    await app.api.anularFactura(factura);
    await app.api.eliminarFactura(factura);
    assert.equal(app.prompts, 0);
    assert.equal(app.confirms, 0);
    gate.resolver(respuesta(pagina([factura])));
    await carga;
    assert.equal(app.document.querySelector('.btn-anular').disabled, false);
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
        assert.equal(app.document.querySelector('.btn-anular').disabled, false);
        assert.equal(app.document.querySelector('.btn-eliminar').disabled, false);
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
    for (const dato of ['42', '30/10/2026', 'Ana', 'Monotributo', 'Local principal', '2', '200,00', 'CAE histórico', conCae.cae, 'Solo lectura']) assert.ok(fila.textContent.includes(dato), dato);
    assert.equal(app.document.querySelector('.btn-anular').disabled, true);
    assert.equal(app.document.querySelector('.btn-eliminar').disabled, true);
    assert.match(app.document.querySelector('.btn-eliminar').getAttribute('aria-label'), /42/);
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

test('la lista elegida en el buscador vuelve al formulario de la factura', async t => {
    const app = await montar({
        resolver: r => {
            if (r.url.endsWith('/catalogos/listas-precio')) {
                return respuesta([{ id: 1, descripcion: 'General' }, { id: 2, descripcion: 'Mayorista' }]);
            }
            if (r.url.includes('/listas-precio-articulos/precio?')) return respuesta(precio);
        }
    });
    t.after(() => app.cerrar());
    app.document.querySelector('.btn-buscar-articulo').click();
    await flush();
    const lista = porId(app, 'explorer-lista-precio');
    assert.ok(lista);
    assert.equal(lista.value, '1');
    lista.value = '2';
    lista.dispatchEvent(new app.window.Event('change', { bubbles: true }));
    await flush();
    const consulta = app.llamados.filter(r => r.url.includes('/listas-precio-articulos/explorer')).at(-1);
    assert.equal(new URL(consulta.url).searchParams.get('listaPrecioId'), '2');
    app.document.querySelector('#explorer-precios tbody button').click();
    await flush();
    assert.equal(porId(app, 'alta-lista-precio').value, '2');
});

test('los comprobantes existentes e internos ofrecen PDF y estado de ARCA', async t => {
    const interno = { ...factura, id: 2, tipoComprobante: 'CONSUMIDOR_FINAL', puedeImprimir: false };
    const app = await montar({ resolver: r => r.url.includes('/facturas?')
        ? respuesta(pagina([factura, interno])) : undefined });
    t.after(() => app.cerrar());
    for (const fila of porId(app, 'facturas-tbody').rows) {
        assert.equal(fila.querySelector('a[target="_blank"]').hidden, false);
        assert.equal(fila.querySelector('.btn-recargar-arca').hidden, false);
        assert.equal(fila.querySelector('.btn-recargar-arca').textContent, 'Estado de ARCA');
    }
    assert.match(porId(app, 'facturas-tbody').rows[1].textContent, /Factura B - Consumidor Final/);
    assert.equal(porId(app, 'alta-tipo').querySelector('[value="CONSUMIDOR_FINAL"]').textContent,
        'Factura B - Consumidor Final');
    assert.match(porId(app, 'facturas-tbody').rows[1].textContent, /No aplica/);
});

test('editar una B permite guardarla como C sin solicitar CAE', async t => {
    const interna = { ...factura, tipoComprobante: 'CONSUMIDOR_FINAL', puedeModificar: true };
    const app = await montar({ resolver: llamada => {
        const url = new URL(llamada.url);
        if (url.pathname.endsWith('/facturas') && llamada.method === 'GET') return respuesta(pagina([interna]));
        if (url.pathname.endsWith('/1/detalle')) return respuesta({
            id: 1, revision: 2, tipoComprobante: 'CONSUMIDOR_FINAL', puedeModificar: true, puedeCambiarTipo: true,
            puntoVentaId: 1, puntosVentaHomologacion: [{ id: 2, descripcion: '99001 · Homologación' }],
            clienteId: null, condicionIvaId: 5, tipoMonedaId: 1, listaPrecioId: 1, fechaEmision: '2026-11-20',
            detalles: [{ ...precio, listaPrecioArticuloId: precio.id, cantidad: 1, porcentajeBonificacion: 0 }]
        });
        if (url.pathname.endsWith('/listas-precio-articulos/precio')) return respuesta(precio);
        if (url.pathname.endsWith('/puntos-venta')) return respuesta([...opcion, { id: 2, descripcion: 'Homologación' }]);
        if (llamada.method === 'PUT') return respuesta({ ...interna, tipoComprobante: 'FACTURA_C' });
    } });
    t.after(() => app.cerrar());
    [...app.document.querySelectorAll('button')].find(boton => boton.textContent === 'Editar').click();
    await flush();
    const tipo = porId(app, 'alta-tipo');
    assert.equal(tipo.disabled, false);
    tipo.value = 'FACTURA_C';
    tipo.dispatchEvent(new app.window.Event('change'));
    assert.equal(porId(app, 'alta-campo-cliente').hidden, false);
    assert.equal(porId(app, 'alta-punto-venta').disabled, false);
    porId(app, 'alta-punto-venta').value = '2';
    porId(app, 'alta-condicion-iva').value = '1';
    porId(app, 'btn-alta-cliente').click();
    await flush();
    app.document.querySelector('#explorer-clientes button[aria-label^="Seleccionar"]').click();
    await flush();
    submit(app, 'form-alta');
    await flush();

    const put = app.llamados.find(llamada => llamada.method === 'PUT');
    assert.ok(put);
    const datos = JSON.parse(put.opciones.body);
    assert.equal(datos.tipoComprobante, 'FACTURA_C');
    assert.equal(datos.puntoVentaId, 2);
    assert.equal(datos.clienteId, 1);
    assert.equal(datos.revision, 2);
    assert.equal(app.llamados.some(llamada => llamada.url.endsWith('/arca/autorizar')), false);
});

test('editar una C permite elegir otro punto de venta al convertirla a B', async t => {
    const fiscal = { ...factura, tipoComprobante: 'FACTURA_C', puedeModificar: true };
    const app = await montar({ resolver: llamada => {
        const url = new URL(llamada.url);
        if (url.pathname.endsWith('/facturas') && llamada.method === 'GET') return respuesta(pagina([fiscal]));
        if (url.pathname.endsWith('/1/detalle')) return respuesta({
            id: 1, revision: 2, tipoComprobante: 'FACTURA_C', puedeModificar: true, puedeCambiarTipo: true,
            puntoVentaId: 2, puntosVentaHomologacion: [],
            clienteId: 1, clienteDenominacion: 'Ana', clienteCuit: '30000000007',
            condicionIvaId: 1, tipoMonedaId: 1, listaPrecioId: 1, fechaEmision: '2026-11-20',
            detalles: [{ ...precio, listaPrecioArticuloId: precio.id, cantidad: 1, porcentajeBonificacion: 0 }]
        });
        if (url.pathname.endsWith('/listas-precio-articulos/precio')) return respuesta(precio);
        if (url.pathname.endsWith('/puntos-venta')) return respuesta([...opcion, { id: 2, descripcion: 'Homologación' }]);
        if (llamada.method === 'PUT') return respuesta({ ...fiscal, tipoComprobante: 'CONSUMIDOR_FINAL' });
    } });
    t.after(() => app.cerrar());
    [...app.document.querySelectorAll('button')].find(boton => boton.textContent === 'Editar').click();
    await flush();
    const tipo = porId(app, 'alta-tipo');
    const punto = porId(app, 'alta-punto-venta');
    assert.equal(punto.disabled, true);
    tipo.value = 'CONSUMIDOR_FINAL';
    tipo.dispatchEvent(new app.window.Event('change'));
    assert.equal(punto.disabled, false);
    punto.value = '1';
    assert.equal(porId(app, 'alta-campo-cliente').hidden, true);
    submit(app, 'form-alta');
    await flush();

    const put = app.llamados.find(llamada => llamada.method === 'PUT');
    assert.ok(put);
    const datos = JSON.parse(put.opciones.body);
    assert.equal(datos.tipoComprobante, 'CONSUMIDOR_FINAL');
    assert.equal(datos.puntoVentaId, 1);
    assert.equal(datos.clienteId, undefined);
    assert.equal(app.llamados.some(llamada => llamada.url.endsWith('/arca/autorizar')), false);
});

test('editar una B permite conservar el tipo y guardar otro punto activo', async t => {
    const interna = { ...factura, tipoComprobante: 'CONSUMIDOR_FINAL', puedeModificar: true };
    const app = await montar({ resolver: llamada => {
        const url = new URL(llamada.url);
        if (url.pathname.endsWith('/facturas') && llamada.method === 'GET') return respuesta(pagina([interna]));
        if (url.pathname.endsWith('/1/detalle')) return respuesta({
            id: 1, revision: 2, tipoComprobante: 'CONSUMIDOR_FINAL', puedeModificar: true, puedeCambiarTipo: true,
            puntoVentaId: 1, puntosVentaHomologacion: [], clienteId: null, condicionIvaId: 5, tipoMonedaId: 1,
            listaPrecioId: 1, fechaEmision: '2026-11-20',
            detalles: [{ ...precio, listaPrecioArticuloId: precio.id, cantidad: 1, porcentajeBonificacion: 0 }]
        });
        if (url.pathname.endsWith('/listas-precio-articulos/precio')) return respuesta(precio);
        if (url.pathname.endsWith('/puntos-venta')) return respuesta([...opcion, { id: 3, descripcion: 'Sucursal' }]);
        if (llamada.method === 'PUT') return respuesta(interna);
    } });
    t.after(() => app.cerrar());
    [...app.document.querySelectorAll('button')].find(boton => boton.textContent === 'Editar').click();
    await flush();

    const punto = porId(app, 'alta-punto-venta');
    assert.equal(punto.disabled, false);
    assert.equal(punto.value, '1');
    punto.value = '3';
    submit(app, 'form-alta');
    await flush();

    const put = app.llamados.find(llamada => llamada.method === 'PUT');
    assert.ok(put);
    const datos = JSON.parse(put.opciones.body);
    assert.equal(datos.tipoComprobante, 'CONSUMIDOR_FINAL');
    assert.equal(datos.puntoVentaId, 3);
    assert.equal(datos.clienteId, undefined);
    assert.equal(app.llamados.some(llamada => llamada.url.endsWith('/arca/autorizar')), false);
});
