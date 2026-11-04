import { montar, flush, precio, respuesta, opcion, factura, pagina } from './appDOM.helpers.mjs';

async function medir(nombre, archivoApp, htmlFile) {
    let reloj = 0;
    const pendientes = [];
    const app = await montar({ archivoApp, htmlFile, reloj: () => reloj, resolver: r => new Promise(resolve => {
        pendientes.push({ cuando: reloj + (r.url.includes('/catalogos/') ? 300 : 200), resolver: () => resolve(respuesta(r.url.includes('/catalogos/') ? opcion : pagina([factura]))) });
    }) });
    let borrador = app.document.getElementById('items-tbody').rows.length ? 0 : null;
    let listado = null;
    while (pendientes.length) {
        reloj = Math.min(...pendientes.map(p => p.cuando));
        const actuales = pendientes.filter(p => p.cuando === reloj);
        for (const actual of actuales) pendientes.splice(pendientes.indexOf(actual), 1);
        actuales.forEach(p => p.resolver());
        await flush();
        if (borrador === null && app.document.getElementById('items-tbody').rows.length) borrador = reloj;
        if (listado === null && app.document.getElementById('facturas-tbody').textContent.includes('42')) listado = reloj;
    }
    const renders = [];
    for (const n of [50, 100, 200]) {
        app.api.setItems(Array.from({ length: n }, () => ({ precio, cantidad: 1, porcentajeBonificacion: 0 })));
        app.metricas.subtotalDe = app.metricas.actualizarTotal = 0;
        app.api.renderizarItems();
        renders.push({ items: n, ...app.metricas });
    }
    const resultado = { nombre, arranque: { latenciaCatalogosMs: 300, latenciaListadoMs: 200, borradorListoMs: borrador, listadoVisibleMs: listado, cargaCompletaMs: reloj, solicitudes: app.llamados.length }, renders };
    app.cerrar();
    return resultado;
}

const [antesJS, antesHTML] = process.argv.slice(2);
const resultados = [];
if (antesJS) resultados.push(await medir('antes', antesJS, antesHTML));
resultados.push(await medir('despues'));
console.log(JSON.stringify(resultados, null, 2));
