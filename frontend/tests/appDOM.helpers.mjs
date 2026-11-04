import { readFile } from 'node:fs/promises';
import { JSDOM } from 'jsdom';

const appURL = new URL('../public/js/app.js', import.meta.url);
let instancia = 0;
export const opcion = [{ id: 1, descripcion: 'Opción uno' }];
export const precio = { id: 11, articuloId: 1, articuloCodigo: 'A1', articuloDenominacion: 'Lápiz', listaPrecioCodigo: 'MIN', listaPrecioDenominacion: 'Minorista', precioVenta: 100 };
export const factura = { id: 1, numeroFactura: 42, fechaEmision: '2026-10-30', clienteDenominacion: 'Ana', condicionIva: 'Monotributo', puntoVentaDescripcion: 'Local principal', cantidadItems: 2, importeTotal: 200, estado: 'PENDIENTE', cae: null };
export const pagina = contenido => ({ contenido, pagina: 0, tamanio: 20, totalElementos: contenido.length, totalPaginas: contenido.length ? 1 : 0 });
export const diferida = () => {
    let resolver;
    const promise = new Promise(resolve => { resolver = resolve; });
    return { promise, resolver };
};
export const respuesta = (body, status = 200) => new Response(status === 204 ? null : JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
export const flush = async () => { for (let i = 0; i < 6; i++) await new Promise(resolve => setImmediate(resolve)); };

export async function montar({ resolver, reducedMotion = false, archivoApp = appURL, htmlFile = new URL('../public/index.html', import.meta.url), reloj = () => 0 } = {}) {
    const dom = new JSDOM(await readFile(htmlFile, 'utf8'), { url: 'http://localhost:3000' });
    const { window } = dom;
    const { document } = window;
    // El HTML anterior no tenía estado/fieldset: solo el harness de comparación los añade.
    if (!document.getElementById('alta-campos')) {
        const fieldset = document.createElement('fieldset');
        fieldset.id = 'alta-campos';
        fieldset.disabled = true;
        const form = document.getElementById('form-alta');
        fieldset.append(...form.childNodes);
        form.append(fieldset);
    }
    if (!document.getElementById('catalogos-estado')) {
        const estado = document.createElement('p');
        estado.id = 'catalogos-estado';
        const retry = document.createElement('button');
        retry.id = 'btn-reintentar-catalogos';
        retry.hidden = true;
        document.getElementById('form-alta').before(estado, retry);
    }
    const llamados = [];
    const scrolls = [];
    let prompts = 0;
    let confirms = 0;
    window.HTMLElement.prototype.scrollIntoView = function (options) { scrolls.push(options); };
    window.matchMedia = () => ({ matches: reducedMotion, addEventListener() {}, removeEventListener() {} });
    window.HTMLDialogElement.prototype.showModal = function () { this.open = true; };
    window.HTMLDialogElement.prototype.close = function () { this.open = false; this.dispatchEvent(new window.Event('close')); };
    Object.assign(globalThis, {
        window, document, Option: window.Option,
        prompt: () => { prompts++; return 'Motivo'; },
        confirm: () => { confirms++; return true; },
        fetch: async (url, opciones = {}) => {
            const llamada = { url: String(url), method: opciones.method ?? 'GET', opciones, tiempo: reloj() };
            llamados.push(llamada);
            const custom = resolver?.(llamada);
            if (custom !== undefined) return (await custom).clone();
            if (llamada.url.includes('/clientes/explorer')) return respuesta(pagina([{ id: 1, denominacion: 'Ana', cuitCuil: '20111' }]));
            if (llamada.url.includes('/listas-precio-articulos/explorer')) return respuesta(pagina([precio]));
            if (llamada.url.includes('/catalogos/')) return respuesta(opcion);
            if (llamada.method === 'GET') return respuesta(pagina([factura]));
            return respuesta(factura, llamada.method === 'DELETE' ? 204 : 200);
        }
    });
    const metricas = { subtotalDe: 0, actualizarTotal: 0 };
    globalThis.__metricasApp = metricas;
    let source = await readFile(archivoApp, 'utf8');
    source = source.replace(/from '(\.\.?\/[^']+)';/g, (_, spec) => `from '${new URL(spec, appURL).href}';`)
        .replace('function subtotalDe(item) {', 'function subtotalDe(item) { globalThis.__metricasApp.subtotalDe++;')
        .replace('function actualizarTotal() {', 'function actualizarTotal() { globalThis.__metricasApp.actualizarTotal++;');
    source += `\n globalThis.__appMedicion = { cargarFacturas, anularFactura, eliminarFactura, renderizarItems, setItems(valor) { items = valor; } };\n//# sourceURL=app-dom-${++instancia}.js`;
    await import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`);
    await flush();
    return {
        window, document, llamados, scrolls, metricas, api: globalThis.__appMedicion,
        get prompts() { return prompts; }, get confirms() { return confirms; },
        cerrar() { dom.window.close(); }
    };
}
