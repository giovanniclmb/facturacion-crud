import { test } from 'node:test';
import assert from 'node:assert/strict';
import { CatalogoService } from '../public/js/services/CatalogoService.js';

test('los explorers envían texto literal y paginación al backend', async () => {
    const original = globalThis.fetch;
    const urls = [];
    globalThis.fetch = async (url) => {
        urls.push(new URL(url));
        return Response.json({ contenido: [], pagina: 2, tamanio: 10, totalElementos: 0, totalPaginas: 0 });
    };
    try {
        const servicio = new CatalogoService();
        await servicio.buscarClientes('Ana & Hijos', 2);
        await servicio.buscarListasPrecioArticulos('10%_Lista', 2);
        assert.equal(urls[0].pathname, '/api/catalogos/clientes/explorer');
        assert.equal(urls[1].pathname, '/api/catalogos/listas-precio-articulos/explorer');
        assert.deepEqual(Object.fromEntries(urls[0].searchParams), {busqueda:'Ana & Hijos', pagina:'2', tamanio:'10'});
        assert.deepEqual(Object.fromEntries(urls[1].searchParams), {busqueda:'10%_Lista', pagina:'2', tamanio:'10'});
    } finally {
        globalThis.fetch = original;
    }
});
