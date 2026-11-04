import { test } from 'node:test';
import assert from 'node:assert/strict';
import { ConsultaExplorer } from '../public/js/components/ConsultaExplorer.js';

async function crearConsulta(buscar) {
    return new ConsultaExplorer(buscar);
}

test('una respuesta anterior no reemplaza la búsqueda más reciente', async () => {
    let terminarAnterior;
    const consulta = await crearConsulta((texto) => texto === 'Ana'
        ? new Promise(resolve => { terminarAnterior = resolve; })
        : Promise.resolve({contenido:[{id:2}], pagina:0,tamanio:10,totalElementos:1,totalPaginas:1}));
    const anterior = consulta.buscar('Ana', 0);
    const actual = await consulta.buscar('Luis', 0);
    terminarAnterior({contenido:[{id:1}],pagina:0,tamanio:10,totalElementos:1,totalPaginas:1});
    assert.equal(actual.contenido[0].id, 2);
    assert.equal(await anterior, undefined);
});

test('cerrar el explorer invalida la respuesta pendiente, incluso un error tardío', async () => {
    let rechazar;
    const consulta = await crearConsulta(() => new Promise((_resolve,reject) => { rechazar = reject; }));
    const pendiente = consulta.buscar('', 0);
    consulta.cancelar();
    rechazar(new Error('Servidor caído'));
    assert.equal(await pendiente, undefined);
});

test('el error de la búsqueda actual se comunica para permitir reintentar', async () => {
    const consulta = await crearConsulta(async () => { throw new Error('Sin conexión'); });
    await assert.rejects(consulta.buscar('Ana', 0), /Sin conexión/);
});
