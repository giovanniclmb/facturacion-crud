import { test } from 'node:test';
import assert from 'node:assert/strict';
import { FacturaService } from '../public/js/services/FacturaService.js';

test('el listado solicita la página indicada y conserva filtros, incluso false', async () => {
    const original = globalThis.fetch;
    let solicitada;
    globalThis.fetch = async (url) => {
        solicitada = new URL(url);
        return Response.json({ contenido: [], pagina: 2, tamanio: 10, totalElementos: 0, totalPaginas: 0 });
    };
    try {
        const resultado = await new FacturaService().obtenerFacturas({
            numeroFactura: 42, clienteId: 7, sinInformarArca: false,
            fechaDesde: '2026-10-01', fechaHasta: '2026-10-30',
            estado: 'EMITIDA', montoMinimo: 100
        }, 2, 10);
        assert.equal(solicitada.pathname, '/api/facturas');
        assert.deepEqual(Object.fromEntries(solicitada.searchParams), {
            numeroFactura: '42', clienteId: '7', sinInformarArca: 'false',
            fechaDesde: '2026-10-01', fechaHasta: '2026-10-30',
            estado: 'EMITIDA', montoMinimo: '100', pagina: '2', tamanio: '10'
        });
        assert.equal(resultado.pagina, 2);
    } finally {
        globalThis.fetch = original;
    }
});

test('el listado usa por defecto la primera página de veinte facturas', async () => {
    const original = globalThis.fetch;
    let solicitada;
    globalThis.fetch = async (url) => {
        solicitada = new URL(url);
        return Response.json({ contenido: [], pagina: 0, tamanio: 20, totalElementos: 0, totalPaginas: 0 });
    };
    try {
        await new FacturaService().obtenerFacturas();
        assert.equal(solicitada.searchParams.get('pagina'), '0');
        assert.equal(solicitada.searchParams.get('tamanio'), '20');
    } finally {
        globalThis.fetch = original;
    }
});

test('las descargas filtran el conjunto completo sin parámetros de página', () => {
    for (const formato of ['pdf', 'excel']) {
        const url = new URL(new FacturaService().urlReporte(formato, {
            clienteId: 7, sinInformarArca: true, fechaHasta: '2026-10-30'
        }));
        assert.equal(url.pathname, `/api/facturas/reporte/${formato}`);
        assert.deepEqual(Object.fromEntries(url.searchParams), {
            clienteId: '7', sinInformarArca: 'true', fechaHasta: '2026-10-30'
        });
    }
});

test('ARCA usa los contratos explícitos, acepta 202 y nunca reintenta POST', async () => {
    const original = globalThis.fetch;
    const llamadas = [];
    globalThis.fetch = async (url, opciones = {}) => {
        llamadas.push([new URL(url).pathname, opciones]);
        return Response.json({ estadoFiscal: 'INCIERTO' }, { status: 202 });
    };
    try {
        const servicio = new FacturaService();
        assert.equal((await servicio.autorizarArca(7, true)).estadoFiscal, 'INCIERTO');
        await servicio.consultarArca(7);
        await servicio.estadoArca(7);
        assert.deepEqual(llamadas.map(([url, op]) => [url, op.method ?? 'GET']), [
            ['/api/facturas/7/arca/autorizar', 'POST'], ['/api/facturas/7/arca/consultar', 'POST'], ['/api/facturas/7/arca', 'GET']
        ]);
        assert.deepEqual(JSON.parse(llamadas[0][1].body), { confirmarCuitReceptor: true });
        globalThis.fetch = async () => { llamadas.push(['fallo']); throw new TypeError('Sin conexión'); };
        await assert.rejects(servicio.autorizarArca(7, true));
        assert.equal(llamadas.length, 4);
    } finally { globalThis.fetch = original; }
});
