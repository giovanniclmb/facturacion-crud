import {
    ClienteExplorerDTO,
    ListaPrecioArticuloExplorerDTO,
    EstadoFactura,
    TipoComprobante,
    FacturaDetalleRequestDTO,
    FacturaFiltrosDTO,
    FacturaReporteDTO,
    FacturaRequestDTO,
    OpcionDTO,
    PaginaDTO
} from './models/factura.dto.js';
import { FacturaService } from './services/FacturaService.js';
import { CatalogoService } from './services/CatalogoService.js';
import { ApiError } from './services/http.js';
import { Explorer } from './components/Explorer.js';
import { AutorizacionArca } from './components/AutorizacionArca.js';
import { permisosFactura } from './models/permisosFactura.js';

// ============================================================
// Servicios y estado en memoria
// ============================================================

const facturaService = new FacturaService();
const catalogoService = new CatalogoService();
const NOMBRE_FACTURA_CONSUMIDOR_FINAL = 'Factura B - Consumidor Final';

/** Un renglón del formulario de alta (lo que el usuario está cargando). */
interface ItemFormulario {
    precio: ListaPrecioArticuloExplorerDTO | null;
    cantidad: number;
    porcentajeBonificacion: number;
    descripcion?: string;
    precioNoDisponible?: boolean;
}

let items: ItemFormulario[] = [];
let clienteAlta: ClienteExplorerDTO | null = null;
let clienteFiltro: ClienteExplorerDTO | null = null;
let filtrosAplicados: FacturaFiltrosDTO = {};
let paginaActual = 0;
let totalPaginas = 0;
let listadoCargando = false;
let ultimaConsulta = 0;
let catalogosCargados = false;
let catalogosCargando = false;
let creandoFactura = false;
let cargandoEdicion = false;
let cotizandoItems = false;
let puntosVentaCatalogo: OpcionDTO[] = [];
let edicion: {
    id: number;
    revision: number;
    tipoOriginal: TipoComprobante | null;
    puntoOriginal: number;
    puntosVentaHomologacion: OpcionDTO[];
} | null = null;
let operacionAlta: string | undefined;
const operacionesEnCurso = new Set<number>();
const recuperacionesArcaPendientes = new Set<number>();
const accionesVisibles = new Map<number, {
    factura: FacturaReporteDTO;
    editar: HTMLButtonElement;
    anular: HTMLButtonElement;
    eliminar: HTMLButtonElement;
    arca: AutorizacionArca;
}>();

// ============================================================
// Referencias al DOM
// ============================================================

/** getElementById tipado: falla enseguida si el id no existe en el HTML. */
function elemento<T extends HTMLElement>(id: string): T {
    const el = document.getElementById(id);
    if (!el) {
        throw new Error(`No existe el elemento #${id} en index.html`);
    }
    return el as T;
}

const mensaje = elemento<HTMLDivElement>('mensaje');

// Filtros
const formFiltros = elemento<HTMLFormElement>('form-filtros');
const filtroNumero = elemento<HTMLInputElement>('filtro-numero');
const filtroCliente = elemento<HTMLInputElement>('filtro-cliente');
const btnFiltroCliente = elemento<HTMLButtonElement>('btn-filtro-cliente');
const btnLimpiarCliente = elemento<HTMLButtonElement>('btn-limpiar-cliente');
const filtroArca = elemento<HTMLSelectElement>('filtro-arca');
const filtroFechaDesde = elemento<HTMLInputElement>('filtro-fecha-desde');
const filtroFechaHasta = elemento<HTMLInputElement>('filtro-fecha-hasta');
const filtroEstado = elemento<HTMLSelectElement>('filtro-estado');
const filtroMontoMinimo = elemento<HTMLInputElement>('filtro-monto-minimo');
const btnLimpiarFiltros = elemento<HTMLButtonElement>('btn-limpiar-filtros');
const btnReportePdf = elemento<HTMLButtonElement>('btn-reporte-pdf');
const btnReporteExcel = elemento<HTMLButtonElement>('btn-reporte-excel');

// Alta
const formAlta = elemento<HTMLFormElement>('form-alta');
const altaCampos = elemento<HTMLFieldSetElement>('alta-campos');
const catalogosEstado = elemento<HTMLParagraphElement>('catalogos-estado');
const btnReintentarCatalogos = elemento<HTMLButtonElement>('btn-reintentar-catalogos');
const altaTipo = elemento<HTMLSelectElement>('alta-tipo');
const altaLista = elemento<HTMLSelectElement>('alta-lista-precio');
const ayudaPuntoFiscal = elemento<HTMLElement>('alta-ayuda-punto-fiscal');
const panelAlta = elemento<HTMLDetailsElement>('panel-alta');
const tituloFormulario = elemento<HTMLSpanElement>('titulo-formulario');
const altaRechazo = elemento<HTMLDivElement>('alta-rechazo');
const btnCancelarEdicion = elemento<HTMLButtonElement>('btn-cancelar-edicion');
const altaPuntoVenta = elemento<HTMLSelectElement>('alta-punto-venta');
const altaCliente = elemento<HTMLInputElement>('alta-cliente');
const btnAltaCliente = elemento<HTMLButtonElement>('btn-alta-cliente');
const altaCondicionIva = elemento<HTMLSelectElement>('alta-condicion-iva');
const altaMoneda = elemento<HTMLSelectElement>('alta-moneda');
const altaFecha = elemento<HTMLInputElement>('alta-fecha');
const altaObservaciones = elemento<HTMLInputElement>('alta-observaciones');
const itemsTbody = elemento<HTMLTableSectionElement>('items-tbody');
const itemsTotal = elemento<HTMLTableCellElement>('items-total');
const btnAgregarItem = elemento<HTMLButtonElement>('btn-agregar-item');
const btnCrear = elemento<HTMLButtonElement>('btn-crear');

// Listado
const facturasTbody = elemento<HTMLTableSectionElement>('facturas-tbody');
const ordenFecha = elemento<HTMLSelectElement>('orden-fecha');
const listadoResumen = elemento<HTMLSpanElement>('listado-resumen');
const paginaResumen = elemento<HTMLSpanElement>('pagina-resumen');
const tamanioPagina = elemento<HTMLSelectElement>('tamanio-pagina');
const btnPaginaAnterior = elemento<HTMLButtonElement>('btn-pagina-anterior');
const btnPaginaSiguiente = elemento<HTMLButtonElement>('btn-pagina-siguiente');

// ============================================================
// Utilidades
// ============================================================

const formatoMoneda = new Intl.NumberFormat('es-AR', { style: 'currency', currency: 'ARS' });

let soloReceptoresFacturaC = false;
const explorerClientes = new Explorer<ClienteExplorerDTO>({
    id: 'explorer-clientes',
    titulo: 'Buscar cliente',
    ayuda: 'Busque por denominación o CUIT/CUIL y seleccione un cliente activo.',
    columnas: [
        { titulo: 'Cliente', valor: c => c.denominacion },
        { titulo: 'CUIT/CUIL', valor: c => c.cuitCuil }
    ],
    describir: c => `${c.denominacion}, CUIT/CUIL ${c.cuitCuil}`,
    buscar: (texto, pagina) => catalogoService.buscarClientes(texto, pagina, soloReceptoresFacturaC || undefined)
});

const listaExplorer = crearElemento('select', 'form-select');
listaExplorer.id = 'explorer-lista-precio';
const etiquetaListaExplorer = crearElemento('label', 'form-label', 'Lista de la factura');
etiquetaListaExplorer.htmlFor = listaExplorer.id;
const filtroListaExplorer = crearElemento('div', 'mt-3');
filtroListaExplorer.append(etiquetaListaExplorer, listaExplorer);

const explorerPrecios = new Explorer<ListaPrecioArticuloExplorerDTO>({
    id: 'explorer-precios',
    titulo: 'Buscar artículo y precio',
    ayuda: 'Si falta un precio en la lista elegida, se usa General.',
    filtro: filtroListaExplorer,
    columnas: [
        { titulo: 'Código', valor: p => p.articuloCodigo },
        { titulo: 'Artículo', valor: p => p.articuloDenominacion },
        { titulo: 'Lista de precios', valor: p => `${p.listaPrecioCodigo} · ${p.listaPrecioDenominacion}` },
        { titulo: 'Precio unitario', valor: p => formatearMoneda(p.precioVenta), numero: true }
    ],
    describir: p => `${p.articuloDenominacion}, lista ${p.listaPrecioCodigo}, ${formatearMoneda(p.precioVenta)}`,
    buscar: (texto, pagina) => catalogoService.buscarListasPrecioArticulos(texto, pagina, idSeleccionado(listaExplorer))
});

function formatearMoneda(valor: number): string {
    return formatoMoneda.format(valor);
}

/** "2026-03-15" → "15/03/2026" */
function formatearFecha(iso: string): string {
    const [anio, mes, dia] = iso.split('-');
    return `${dia}/${mes}/${anio}`;
}

/** Fecha de hoy en hora LOCAL como "yyyy-MM-dd" (toISOString usaría UTC). */
function hoyISO(): string {
    const hoy = new Date();
    const mes = String(hoy.getMonth() + 1).padStart(2, '0');
    const dia = String(hoy.getDate()).padStart(2, '0');
    return `${hoy.getFullYear()}-${mes}-${dia}`;
}

/** Mismo redondeo que el backend: 2 decimales. */
function redondear(valor: number): number {
    return Math.round(valor * 100) / 100;
}

function crearElemento<K extends keyof HTMLElementTagNameMap>(
    etiqueta: K, clase = '', texto = ''): HTMLElementTagNameMap[K] {
    const el = document.createElement(etiqueta);
    if (clase) el.className = clase;
    if (texto) el.textContent = texto;
    return el;
}

// ============================================================
// Mensajes al usuario
// ============================================================

function mostrarMensaje(tipo: 'success' | 'danger' | 'warning', texto: string, detalles: string[] = []): void {
    mensaje.className = `alert alert-${tipo}`;
    mensaje.replaceChildren(crearElemento('div', '', texto));

    if (detalles.length > 0) {
        const lista = crearElemento('ul', 'mb-0 mt-2');
        detalles.forEach(d => lista.appendChild(crearElemento('li', '', d)));
        mensaje.appendChild(lista);
    }
    mensaje.scrollIntoView({
        behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth',
        block: 'nearest'
    });
}

function ocultarMensaje(): void {
    mensaje.className = 'alert d-none';
    mensaje.replaceChildren();
}

function mostrarError(error: unknown): void {
    if (error instanceof ApiError) {
        mostrarMensaje('danger', error.message, error.detalles);
    } else {
        mostrarMensaje('danger', 'Error inesperado: ' + String(error));
        console.error(error);
    }
}

// ============================================================
// Catálogos (selects del formulario de alta)
// ============================================================

function llenarSelect(select: HTMLSelectElement, opciones: OpcionDTO[], textoVacio: string): void {
    select.replaceChildren(new Option(textoVacio, ''));
    opciones.forEach(o => select.appendChild(new Option(o.descripcion, String(o.id))));
}

async function cargarCatalogos(): Promise<void> {
    if (catalogosCargando) return;
    catalogosCargando = true;
    catalogosCargados = false;
    altaCampos.disabled = true;
    btnCrear.disabled = true;
    btnReintentarCatalogos.hidden = true;
    btnReintentarCatalogos.disabled = true;
    catalogosEstado.classList.remove('text-danger');
    catalogosEstado.classList.add('text-muted');
    catalogosEstado.textContent = 'Cargando opciones para crear la factura…';
    try {
        // Solo los catálogos pequeños; clientes y precios se consultan en sus explorers.
        const [puntosVenta, condicionesIva, monedas, listas] = await Promise.all([
            catalogoService.obtenerPuntosVenta(),
            catalogoService.obtenerCondicionesIva(),
            catalogoService.obtenerMonedas(),
            catalogoService.obtenerListasPrecio()
        ]);
        puntosVentaCatalogo = puntosVenta;
        llenarSelect(altaPuntoVenta, puntosVentaCatalogo, 'Seleccione…');
        llenarSelect(altaCondicionIva, condicionesIva, 'Seleccione…');
        llenarSelect(altaMoneda, monedas, 'Seleccione…');
        llenarSelect(altaLista, listas, 'Seleccione…');
        seleccionarGeneral();
        catalogosCargados = true;
        catalogosEstado.textContent = '';
    } catch (error) {
        const detalle = error instanceof Error ? error.message : 'Vuelva a intentarlo.';
        catalogosEstado.textContent = `No se pudieron cargar las opciones para crear facturas. ${detalle}`;
        catalogosEstado.classList.remove('text-muted');
        catalogosEstado.classList.add('text-danger');
        btnReintentarCatalogos.hidden = false;
    } finally {
        catalogosCargando = false;
        altaCampos.disabled = !catalogosCargados;
        btnCrear.disabled = !catalogosCargados || creandoFactura;
        btnReintentarCatalogos.disabled = false;
    }
}

// ============================================================
// Ítems del alta (tabla dinámica)
// ============================================================

function subtotalDe(item: ItemFormulario): number {
    const bruto = item.cantidad * (item.precioNoDisponible ? 0 : (item.precio?.precioVenta ?? 0));
    return redondear(bruto - bruto * item.porcentajeBonificacion / 100);
}

function actualizarTotal(): void {
    const total = items.reduce((suma, item) => suma + subtotalDe(item), 0);
    itemsTotal.textContent = formatearMoneda(redondear(total));
}

function crearInputNumero(valor: number, min: string, max: string | null, step: string,
                          alCambiar: (valor: number) => void): HTMLInputElement {
    const input = crearElemento('input', 'form-control');
    input.type = 'number';
    input.min = min;
    if (max !== null) input.max = max;
    input.step = step;
    input.value = String(valor);
    input.addEventListener('input', () => {
        alCambiar(Number.isNaN(input.valueAsNumber) ? 0 : input.valueAsNumber);
    });
    return input;
}

function crearFilaItem(item: ItemFormulario, indice: number): HTMLTableRowElement {
    const fila = crearElemento('tr');
    const celdaPrecio = crearElemento('td', 'numero');
    const celdaSubtotal = crearElemento('td', 'numero');

    // Recalcula solo esta fila y el total (sin redibujar la tabla, para no perder el foco)
    const refrescarFila = (): void => {
        celdaPrecio.textContent = item.precioNoDisponible ? 'Sin precio vigente' : formatearMoneda(item.precio?.precioVenta ?? 0);
        celdaSubtotal.textContent = formatearMoneda(subtotalDe(item));
    };

    const seleccionArticulo = crearElemento('div', 'articulo-seleccion');
    const descripcionArticulo = crearElemento('div');
    const listaPrecio = crearElemento('div', 'small text-muted');
    const btnBuscarArticulo = crearElemento('button', 'btn btn-outline-primary mt-2 btn-buscar-articulo', 'Buscar artículo');
    btnBuscarArticulo.type = 'button';
    btnBuscarArticulo.setAttribute('aria-label', `Buscar artículo para ítem ${indice + 1}`);
    const mostrarArticulo = (): void => {
        descripcionArticulo.textContent = item.precio
            ? `${item.precio.articuloCodigo} · ${item.precio.articuloDenominacion}` : 'Sin artículo seleccionado';
        listaPrecio.textContent = item.precio
            ? `Lista ${item.precio.listaPrecioCodigo} · ${item.precio.listaPrecioDenominacion}` : '';
    };
    btnBuscarArticulo.addEventListener('click', async () => {
        listaExplorer.replaceChildren(...Array.from(altaLista.options)
            .filter(opcion => opcion.value !== '').map(opcion => new Option(opcion.text, opcion.value)));
        listaExplorer.value = altaLista.value;
        const precio = await explorerPrecios.abrir();
        if (precio) {
            item.precio = precio;
            item.precioNoDisponible = false;
            item.descripcion = undefined;
            mostrarArticulo();
            refrescarFila();
            actualizarTotal();
        }
        if (listaExplorer.value && listaExplorer.value !== altaLista.value) {
            altaLista.value = listaExplorer.value;
            await recotizarItems();
            itemsTbody.rows[indice]?.querySelector<HTMLButtonElement>('.btn-buscar-articulo')?.focus();
        }
    });
    mostrarArticulo();
    seleccionArticulo.append(descripcionArticulo, listaPrecio, btnBuscarArticulo);

    // Cantidad y bonificación
    const inputCantidad = crearInputNumero(item.cantidad, '0.01', null, '0.01', v => {
        item.cantidad = v;
        refrescarFila();
        actualizarTotal();
    });
    const inputBonificacion = crearInputNumero(item.porcentajeBonificacion, '0', '100', '0.5', v => {
        item.porcentajeBonificacion = v;
        refrescarFila();
        actualizarTotal();
    });
    inputCantidad.setAttribute('aria-label', `Cantidad del ítem ${indice + 1}`);
    inputBonificacion.setAttribute('aria-label', `Bonificación del ítem ${indice + 1}`);

    // Quitar
    const btnQuitar = crearElemento('button', 'btn btn-outline-danger btn-quitar-item', 'Quitar');
    btnQuitar.type = 'button';
    btnQuitar.setAttribute('aria-label', `Quitar ítem ${indice + 1}`);
    btnQuitar.disabled = items.length === 1; // siempre queda al menos un ítem
    btnQuitar.addEventListener('click', () => {
        const posicion = items.indexOf(item);
        if (items.length === 1 || posicion < 0) return;
        const focoEnFila = fila.contains(document.activeElement);
        const vecina = fila.nextElementSibling ?? fila.previousElementSibling;
        items.splice(posicion, 1);
        fila.remove();
        actualizarControlesItems();
        actualizarTotal();
        if (focoEnFila) {
            (vecina?.querySelector<HTMLInputElement>('input') ?? btnAgregarItem).focus();
        }
    });

    const celda = (contenido: HTMLElement): HTMLTableCellElement => {
        const td = crearElemento('td');
        td.appendChild(contenido);
        return td;
    };

    fila.append(celda(seleccionArticulo), celda(inputCantidad), celda(inputBonificacion),
                celdaPrecio, celdaSubtotal, celda(btnQuitar));
    refrescarFila();
    return fila;
}

function renderizarItems(): void {
    itemsTbody.replaceChildren(...items.map((item, i) => crearFilaItem(item, i)));
    actualizarTotal();
}

function actualizarControlesItems(): void {
    Array.from(itemsTbody.rows).forEach((fila, indice) => {
        const inputs = fila.querySelectorAll('input');
        inputs[0].setAttribute('aria-label', `Cantidad del ítem ${indice + 1}`);
        inputs[1].setAttribute('aria-label', `Bonificación del ítem ${indice + 1}`);
        fila.querySelector('.btn-buscar-articulo')?.setAttribute('aria-label', `Buscar artículo para ítem ${indice + 1}`);
        const quitar = fila.querySelector<HTMLButtonElement>('.btn-quitar-item')!;
        quitar.setAttribute('aria-label', `Quitar ítem ${indice + 1}`);
        quitar.disabled = items.length === 1;
    });
}

function agregarItem(): void {
    const item: ItemFormulario = { precio: null, cantidad: 1, porcentajeBonificacion: 0 };
    items.push(item);
    itemsTbody.append(crearFilaItem(item, items.length - 1));
    actualizarControlesItems();
    actualizarTotal();
}

function reiniciarFormularioAlta(): void {
    if (edicion) llenarSelect(altaPuntoVenta, puntosVentaCatalogo, 'Seleccione…');
    edicion = null;
    operacionAlta = undefined;
    formAlta.reset();
    seleccionarGeneral();
    clienteAlta = null;
    altaPuntoVenta.disabled = false;
    altaTipo.disabled = false;
    altaRechazo.hidden = true;
    btnCancelarEdicion.hidden = true;
    tituloFormulario.textContent = 'Nueva factura';
    sincronizarTipo();
    altaFecha.value = hoyISO();
    items = [];
    itemsTbody.replaceChildren();
    agregarItem();
}

function seleccionarGeneral(): void {
    const general = Array.from(altaLista.options).find(opcion => /^(general$|lista general)/i.test(opcion.text.trim()));
    altaLista.value = general?.value ?? '';
}

function esInterno(): boolean {
    return altaTipo.value === 'CONSUMIDOR_FINAL';
}

function sincronizarTipo(): void {
    for (const id of ['alta-campo-cliente', 'alta-campo-iva']) {
        elemento(id).hidden = esInterno();
    }
    btnCrear.textContent = esInterno() ? 'Guardar Factura B' : 'Guardar Factura C';
    ayudaPuntoFiscal.hidden = true;
    if (!edicion) return;

    const convirtiendo = edicion.tipoOriginal === 'CONSUMIDOR_FINAL' && !esInterno();
    const puntosFiscales = edicion.puntosVentaHomologacion;
    const permiteElegirPunto = convirtiendo && puntosFiscales.length > 0;
    const puntoElegido = idSeleccionado(altaPuntoVenta) ?? edicion.puntoOriginal;
    llenarSelect(altaPuntoVenta, permiteElegirPunto ? puntosFiscales : puntosVentaCatalogo, 'Seleccione…');
    altaPuntoVenta.value = permiteElegirPunto
        ? (puntosFiscales.some(punto => punto.id === puntoElegido) ? String(puntoElegido) : '')
        : String(edicion.puntoOriginal);
    altaPuntoVenta.disabled = !permiteElegirPunto;
    ayudaPuntoFiscal.hidden = !convirtiendo;
    ayudaPuntoFiscal.textContent = permiteElegirPunto
        ? 'Seleccioná un punto habilitado para homologación.'
        : 'Configurá un punto habilitado para poder homologar.';
}

async function recotizarItems(): Promise<void> {
    const lista = idSeleccionado(altaLista);
    if (!lista) return;
    cotizandoItems = true;
    altaCampos.disabled = true;
    catalogosEstado.textContent = 'Consultando precios vigentes…';
    try {
        await Promise.all(items.map(async item => {
            if (!item.precio) return;
            try {
                item.precio = await catalogoService.cotizar(item.precio.articuloId, lista);
                item.precioNoDisponible = false;
            } catch {
                item.precioNoDisponible = true;
            }
        }));
        renderizarItems();
    } finally {
        catalogosEstado.textContent = '';
        cotizandoItems = false;
        altaCampos.disabled = !catalogosCargados || creandoFactura;
    }
}

async function editarFactura(factura: FacturaReporteDTO): Promise<void> {
    if (creandoFactura || cargandoEdicion || cotizandoItems) return;
    if (!catalogosCargados) {
        mostrarMensaje('warning', 'Esperá a que se carguen las opciones del formulario.');
        return;
    }
    cargandoEdicion = true;
    try {
        const detalle = await facturaService.obtenerDetalle(factura.id);
        if (!detalle.puedeModificar) {
            mostrarMensaje('warning', 'La factura ya no está disponible para editar.');
            await cargarFacturas();
            return;
        }
        reiniciarFormularioAlta();
        edicion = {
            id: detalle.id, revision: detalle.revision, tipoOriginal: detalle.tipoComprobante,
            puntoOriginal: detalle.puntoVentaId, puntosVentaHomologacion: detalle.puntosVentaHomologacion
        };
        altaTipo.value = detalle.tipoComprobante ?? 'FACTURA_C';
        altaTipo.disabled = !detalle.puedeCambiarTipo;
        altaPuntoVenta.value = String(detalle.puntoVentaId);
        altaMoneda.value = String(detalle.tipoMonedaId);
        altaCondicionIva.value = esInterno() ? '' : String(detalle.condicionIvaId);
        if (detalle.listaPrecioId !== null) altaLista.value = String(detalle.listaPrecioId);
        altaFecha.value = detalle.fechaEmision;
        altaObservaciones.value = detalle.observaciones ?? '';
        if (detalle.clienteId !== null) {
            clienteAlta = { id: detalle.clienteId, denominacion: detalle.clienteDenominacion ?? '', cuitCuil: detalle.clienteCuit ?? '' };
            altaCliente.value = `${clienteAlta.denominacion} (${clienteAlta.cuitCuil})`;
        }
        items = detalle.detalles.map(item => ({
            precio: { ...item, id: item.listaPrecioArticuloId },
            descripcion: item.descripcion ?? undefined,
            cantidad: item.cantidad,
            porcentajeBonificacion: item.porcentajeBonificacion
        }));
        if (items.length === 0) agregarItem();
        else renderizarItems();
        tituloFormulario.textContent = `Editar comprobante · N.º local ${factura.numeroFactura}`;
        btnCancelarEdicion.hidden = false;
        if (factura.estadoFiscal === 'RECHAZADO') {
            altaRechazo.textContent = factura.mensajeArca || 'Solicitud rechazada por ARCA.';
            altaRechazo.hidden = false;
        }
        sincronizarTipo();
        panelAlta.open = true;
        panelAlta.querySelector('summary')?.focus();
        await recotizarItems();
    } catch (error) {
        mostrarError(error);
    } finally {
        cargandoEdicion = false;
    }
}

// ============================================================
// Alta de factura
// ============================================================

function idSeleccionado(select: HTMLSelectElement): number | undefined {
    return select.value ? Number(select.value) : undefined;
}

/** Validaciones rápidas antes de llamar al backend (que igual valida todo). */
function validarAlta(): string[] {
    const errores: string[] = [];
    if (edicion?.tipoOriginal === 'CONSUMIDOR_FINAL' && !esInterno() && edicion.puntosVentaHomologacion.length === 0) {
        errores.push('Configurá un punto habilitado para convertir el comprobante a Factura C');
    } else if (!altaPuntoVenta.value) {
        errores.push('Seleccione el punto de venta');
    }
    if (!esInterno() && !clienteAlta) errores.push('Seleccione el cliente');
    if (!esInterno() && !altaCondicionIva.value) errores.push('Seleccione la condición de IVA');
    if (!altaLista.value) errores.push('Seleccione la lista de precios');
    if (!altaMoneda.value) errores.push('Seleccione la moneda');
    items.forEach((item, i) => {
        if (item.precioNoDisponible) errores.push(`Ítem ${i + 1}: no hay precio vigente`);
        if (!item.precio) errores.push(`Ítem ${i + 1}: seleccione el artículo y su lista de precios`);
        if (item.cantidad <= 0) errores.push(`Ítem ${i + 1}: la cantidad debe ser mayor que 0`);
        if (item.porcentajeBonificacion < 0 || item.porcentajeBonificacion > 100) {
            errores.push(`Ítem ${i + 1}: la bonificación debe estar entre 0 y 100`);
        }
    });
    return errores;
}

function leerFormularioAlta(): FacturaRequestDTO {
    const detalles: FacturaDetalleRequestDTO[] = items.map(item => ({
        articuloId: item.precio!.articuloId,   // validarAlta garantiza la selección
        descripcion: item.descripcion,
        cantidad: item.cantidad,
        porcentajeBonificacion: item.porcentajeBonificacion
    }));

    return {
        puntoVentaId: idSeleccionado(altaPuntoVenta),
        tipoComprobante: altaTipo.value as TipoComprobante,
        listaPrecioId: idSeleccionado(altaLista),
        revision: edicion?.revision,
        operacionGuardado: operacionAlta,
        clienteId: esInterno() ? undefined : clienteAlta?.id,
        condicionIvaId: esInterno() ? undefined : idSeleccionado(altaCondicionIva),
        tipoMonedaId: idSeleccionado(altaMoneda),
        fechaEmision: altaFecha.value || undefined,
        observaciones: altaObservaciones.value.trim(),
        detalles
    };
}

async function crearFactura(evento: SubmitEvent): Promise<void> {
    evento.preventDefault(); // evita que el formulario recargue la página
    if (!catalogosCargados || creandoFactura || cargandoEdicion || cotizandoItems) return;

    const errores = validarAlta();
    if (errores.length > 0) {
        mostrarMensaje('warning', 'Revise los datos de la factura:', errores);
        return;
    }

    creandoFactura = true;
    if (!edicion) operacionAlta ??= crypto.randomUUID();
    altaCampos.disabled = true;
    btnCrear.textContent = 'Guardando…';
    const solicitud = leerFormularioAlta();
    try {
        const guardada = edicion
            ? await facturaService.guardarEdicion(edicion.id, solicitud)
            : await facturaService.crearFactura(solicitud);
        const referencia = `Comprobante N.º local ${guardada.numeroFactura} guardado.`;
        if (solicitud.tipoComprobante === 'FACTURA_C' && !guardada.cae?.trim()) {
            const pasoArca = guardada.puedeAutorizar ? 'Podés solicitar el CAE desde Comprobantes.'
                : guardada.mensajeArca || 'Revisá el estado de ARCA desde Comprobantes.';
            mostrarMensaje('success', `${referencia} ${pasoArca}`);
        } else {
            mostrarMensaje('success', referencia);
        }
        reiniciarFormularioAlta();
        panelAlta.open = false;
        await cargarFacturas(0);
    } catch (error) {
        if (edicion && error instanceof ApiError && error.status === 409) {
            mostrarMensaje('warning', error.message);
            await cargarFacturas(0);
        } else {
            mostrarError(error);
        }
    } finally {
        creandoFactura = false;
        altaCampos.disabled = !catalogosCargados;
        sincronizarTipo();
    }
}

// ============================================================
// Listado y acciones por fila
// ============================================================

function leerFiltros(): FacturaFiltrosDTO {
    return {
        ordenFecha: ordenFecha.value === 'ASC' ? 'ASC' : 'DESC',
        numeroFactura: filtroNumero.value ? filtroNumero.valueAsNumber : undefined,
        clienteId: clienteFiltro?.id,
        sinInformarArca: filtroArca.value === '' ? undefined : filtroArca.value === 'true',
        fechaDesde: filtroFechaDesde.value || undefined,
        fechaHasta: filtroFechaHasta.value || undefined,
        estado: (filtroEstado.value || undefined) as EstadoFactura | undefined,
        montoMinimo: Number.isNaN(filtroMontoMinimo.valueAsNumber) ? undefined : filtroMontoMinimo.valueAsNumber
    };
}

const CLASE_ESTADO: Record<EstadoFactura, string> = {
    PENDIENTE: 'text-bg-warning',
    EMITIDA: 'text-bg-success',
    ANULADA: 'text-bg-secondary'
};

function crearFilaFactura(f: FacturaReporteDTO): HTMLTableRowElement {
    const fila = crearElemento('tr');
    fila.setAttribute('role', 'row');
    if (f.estado === 'ANULADA') fila.classList.add('text-muted');
    const celda = (etiqueta: string, clase: string): HTMLTableCellElement => {
        const td = crearElemento('td', clase);
        td.dataset.label = etiqueta;
        td.setAttribute('role', 'cell');
        return td;
    };
    // textContent: los datos de API siempre se presentan como texto.
    const comprobante = celda('Comprobante', 'comprobante');
    const numero = crearElemento('div', 'fila-principal');
    const interno = f.tipoComprobante === 'CONSUMIDOR_FINAL';
    const etiquetaNumero = f.estadoFiscal === 'AUTORIZADO' && f.cae && f.puntoVentaFiscal != null && f.numeroFiscal != null
        ? `Factura C ${String(f.puntoVentaFiscal).padStart(5, '0')}-${String(f.numeroFiscal).padStart(8, '0')}`
        : `${interno ? NOMBRE_FACTURA_CONSUMIDOR_FINAL : 'Factura C'} · N° local ${f.numeroFactura ?? '—'}`;
    numero.append(crearElemento('strong', '', etiquetaNumero));
    comprobante.append(numero, crearElemento('div', 'fila-secundaria',
        `${formatearFecha(f.fechaEmision)} · ${f.puntoVentaDescripcion}`));
    const cliente = celda('Cliente', 'cliente');
    cliente.append(crearElemento('div', 'fila-principal', f.clienteDenominacion),
        crearElemento('div', 'fila-secundaria', f.condicionIva));
    const importe = celda('Importe', 'numero');
    importe.append(crearElemento('div', 'fila-principal', formatearMoneda(f.importeTotal)),
        crearElemento('div', 'fila-secundaria', `${f.cantidadItems} ${f.cantidadItems === 1 ? 'ítem' : 'ítems'}`));
    const estado = celda('Estado y ARCA', 'estado-factura');
    const estadoPrincipal = crearElemento('div', 'fila-principal');
    const estadoVisual = f.estado === 'ANULADA' ? ['ANULADA', 'text-bg-secondary']
        : interno ? ['Interno', 'text-bg-secondary']
        : f.estadoFiscal === 'RECHAZADO' ? ['Rechazada', 'text-bg-danger']
        : f.estadoFiscal === 'INCIERTO' ? ['Resultado incierto', 'text-bg-warning']
        : f.estadoFiscal === 'AUTORIZADO' ? ['Autorizada', 'text-bg-success']
        : f.cae?.trim() ? ['CAE histórico', 'text-bg-secondary']
        : [f.estado, CLASE_ESTADO[f.estado] ?? 'text-bg-light'];
    estadoPrincipal.append(crearElemento('span', `badge ${estadoVisual[1]}`, estadoVisual[0]));
    estado.append(estadoPrincipal);

    const celdaAcciones = celda('Acciones', 'acciones');
    const permisos = permisosFactura(f);
    const btnAnular = crearElemento('button', 'btn btn-outline-warning btn-anular me-1', 'Anular');
    btnAnular.type = 'button';
    btnAnular.setAttribute('aria-label', `Anular factura N° ${f.numeroFactura}, ${f.puntoVentaDescripcion}`);
    btnAnular.disabled = listadoCargando || operacionesEnCurso.has(f.id) || !permisos.anular;
    btnAnular.addEventListener('click', () => { void anularFactura(f); });
    const btnEliminar = crearElemento('button', 'btn btn-outline-danger btn-eliminar', 'Eliminar');
    btnEliminar.type = 'button';
    btnEliminar.setAttribute('aria-label', `Eliminar factura N° ${f.numeroFactura}, ${f.puntoVentaDescripcion}`);
    btnEliminar.disabled = listadoCargando || operacionesEnCurso.has(f.id) || !permisos.eliminar;
    btnEliminar.addEventListener('click', () => { void eliminarFactura(f); });
    const btnEditar = crearElemento('button', 'btn btn-outline-primary me-1', 'Editar');
    btnEditar.type = 'button';
    btnEditar.hidden = !permisos.modificar;
    btnEditar.addEventListener('click', () => { void editarFactura(f); });
    const ver = crearElemento('a', 'btn btn-outline-primary me-1', 'Ver comprobante');
    ver.href = facturaService.urlComprobante(f.id);
    ver.target = '_blank';
    ver.rel = 'noopener';
    const arca = new AutorizacionArca(f, facturaService, {
        bloqueada: () => listadoCargando || operacionesEnCurso.has(f.id) || recuperacionesArcaPendientes.has(f.id),
        recuperacionPendiente: () => recuperacionesArcaPendientes.has(f.id) && !operacionesEnCurso.has(f.id),
        iniciar: () => { operacionesEnCurso.add(f.id); actualizarAcciones(); },
        finalizar: () => { operacionesEnCurso.delete(f.id); actualizarAcciones(); },
        bloquearRecuperacion: bloquear => {
            if (bloquear) recuperacionesArcaPendientes.add(f.id);
            else recuperacionesArcaPendientes.delete(f.id);
            actualizarAcciones();
        },
        estadoActualizado: (resultado, recargarComprobantes) => {
            // Una recarga puede haber reemplazado la fila mientras el POST estaba pendiente.
            const visible = accionesVisibles.get(f.id);
            visible?.arca.actualizarDatos(resultado);
            if (recargarComprobantes) {
                void cargarFacturas();
            }
            if (resultado.estadoFiscal === 'AUTORIZADO' && resultado.cae?.trim() && fila.isConnected) {
                estadoPrincipal.firstElementChild!.textContent = 'Autorizada';
                estadoPrincipal.firstElementChild!.className = 'badge text-bg-success';
                if (resultado.numeroFiscal != null) {
                    numero.firstElementChild!.textContent = `Factura C ${String(resultado.puntoVenta).padStart(5, '0')}-${String(resultado.numeroFiscal).padStart(8, '0')}`;
                }
            }
        }
    });
    estado.append(arca.estado);
    celdaAcciones.append(ver, arca.recargar, btnEditar, btnAnular, btnEliminar, arca.acciones);
    accionesVisibles.set(f.id, { factura: f, editar: btnEditar, anular: btnAnular, eliminar: btnEliminar, arca });
    fila.append(comprobante, cliente, importe, estado, celdaAcciones);
    return fila;
}

function actualizarAcciones(): void {
    for (const [id, controles] of accionesVisibles) {
        const permisos = permisosFactura(controles.factura);
        const bloqueada = listadoCargando || operacionesEnCurso.has(id) || recuperacionesArcaPendientes.has(id);
        controles.editar.disabled = bloqueada || !permisos.modificar;
        controles.editar.hidden = !permisos.modificar;
        controles.anular.disabled = bloqueada || !permisos.anular;
        controles.eliminar.disabled = bloqueada || !permisos.eliminar;
        controles.arca.actualizar();
    }
}

function renderizarFacturas(pagina: PaginaDTO<FacturaReporteDTO>): void {
    const facturas = pagina.contenido;
    accionesVisibles.clear();
    if (facturas.length === 0) {
        const fila = crearElemento('tr', 'fila-vacia');
        const celda = crearElemento('td', 'text-center text-muted py-4', 'No hay comprobantes para los filtros seleccionados');
        celda.colSpan = 5;
        fila.appendChild(celda);
        facturasTbody.replaceChildren(fila);
    } else {
        facturasTbody.replaceChildren(...facturas.map(crearFilaFactura));
    }

    const total = facturas.reduce((suma, f) => suma + f.importeTotal, 0);
    const desde = facturas.length === 0 ? 0 : pagina.pagina * pagina.tamanio + 1;
    const hasta = facturas.length === 0 ? 0 : desde + facturas.length - 1;
    listadoResumen.textContent = `${desde}–${hasta} de ${pagina.totalElementos} ${pagina.totalElementos === 1 ? 'comprobante' : 'comprobantes'} · Total de esta página: ${formatearMoneda(total)}`;
    paginaResumen.textContent = pagina.totalPaginas === 0
        ? 'Sin resultados' : `Página ${pagina.pagina + 1} de ${pagina.totalPaginas}`;
}

function actualizarPaginacion(): void {
    actualizarAcciones();
    btnPaginaAnterior.disabled = listadoCargando || paginaActual <= 0;
    btnPaginaSiguiente.disabled = listadoCargando || paginaActual + 1 >= totalPaginas;
    tamanioPagina.disabled = listadoCargando;
    btnReportePdf.disabled = listadoCargando;
    btnReporteExcel.disabled = listadoCargando;
    facturasTbody.closest('table')?.setAttribute('aria-busy', String(listadoCargando));
}

async function cargarFacturas(pagina = paginaActual): Promise<void> {
    const consulta = ++ultimaConsulta;
    listadoCargando = true;
    listadoResumen.textContent = 'Cargando comprobantes…';
    actualizarPaginacion();
    try {
        let resultado = await facturaService.obtenerFacturas(filtrosAplicados, pagina, Number(tamanioPagina.value));
        if (consulta !== ultimaConsulta) return;
        // Al eliminar la última fila de la última página, volvemos a una página existente.
        if (resultado.pagina > 0 && resultado.pagina >= resultado.totalPaginas) {
            resultado = await facturaService.obtenerFacturas(
                filtrosAplicados, Math.max(0, resultado.totalPaginas - 1), Number(tamanioPagina.value));
            if (consulta !== ultimaConsulta) return;
        }
        paginaActual = resultado.pagina;
        totalPaginas = resultado.totalPaginas;
        renderizarFacturas(resultado);
    } catch (error) {
        if (consulta !== ultimaConsulta) return;
        // No dejar datos de una búsqueda anterior bajo los filtros nuevos.
        facturasTbody.replaceChildren();
        accionesVisibles.clear();
        totalPaginas = 0;
        paginaActual = 0;
        listadoResumen.textContent = 'No se pudo cargar el listado. Vuelva a buscar para reintentar.';
        paginaResumen.textContent = 'Listado no disponible';
        mostrarError(error);
    } finally {
        if (consulta === ultimaConsulta) {
            listadoCargando = false;
            actualizarPaginacion();
        }
    }
}

async function anularFactura(f: FacturaReporteDTO): Promise<void> {
    if (listadoCargando || operacionesEnCurso.has(f.id) || recuperacionesArcaPendientes.has(f.id)) return;
    const permisos = permisosFactura(f);
    if (!permisos.anular) {
        mostrarMensaje('warning', permisos.motivo);
        return;
    }
    const motivo = prompt(`Motivo de la anulación de la factura N° ${f.numeroFactura}:`);
    if (motivo === null) return; // canceló

    operacionesEnCurso.add(f.id);
    actualizarAcciones();
    try {
        await facturaService.actualizarEstado(f.id, 'ANULADA', motivo);
        mostrarMensaje('success', `Factura N° ${f.numeroFactura} anulada.`);
        await cargarFacturas();
    } catch (error) {
        mostrarError(error);
    } finally {
        operacionesEnCurso.delete(f.id);
        actualizarAcciones();
    }
}

async function eliminarFactura(f: FacturaReporteDTO): Promise<void> {
    if (listadoCargando || operacionesEnCurso.has(f.id) || recuperacionesArcaPendientes.has(f.id)) return;
    const permisos = permisosFactura(f);
    if (!permisos.eliminar) {
        mostrarMensaje('warning', permisos.motivo);
        return;
    }
    if (!confirm(`¿Eliminar definitivamente la factura N° ${f.numeroFactura}? Esta acción no se puede deshacer.`)) {
        return;
    }

    operacionesEnCurso.add(f.id);
    actualizarAcciones();
    try {
        await facturaService.eliminarFactura(f.id);
        mostrarMensaje('success', `Factura N° ${f.numeroFactura} eliminada.`);
        await cargarFacturas();
    } catch (error) {
        mostrarError(error);
    } finally {
        operacionesEnCurso.delete(f.id);
        actualizarAcciones();
    }
}

// ============================================================
// Eventos e inicio
// ============================================================

formFiltros.addEventListener('submit', evento => {
    evento.preventDefault();
    validarFechasFiltro();
    if (!formFiltros.reportValidity()) return;
    ocultarMensaje();
    filtrosAplicados = leerFiltros();
    cargarFacturas(0);
});

function validarFechasFiltro(): void {
    filtroFechaHasta.setCustomValidity(
        filtroFechaDesde.value && filtroFechaHasta.value && filtroFechaDesde.value > filtroFechaHasta.value
            ? 'La fecha hasta debe ser igual o posterior a la fecha desde.' : '');
}

filtroFechaDesde.addEventListener('input', validarFechasFiltro);
filtroFechaHasta.addEventListener('input', validarFechasFiltro);

btnLimpiarFiltros.addEventListener('click', () => {
    formFiltros.reset();
    clienteFiltro = null;
    filtroFechaHasta.setCustomValidity('');
    filtrosAplicados = { ordenFecha: ordenFecha.value === 'ASC' ? 'ASC' : 'DESC' };
    ocultarMensaje();
    cargarFacturas(0);
});

btnReportePdf.addEventListener('click', () => {
    window.location.href = facturaService.urlReporte('pdf', filtrosAplicados);
});

btnReporteExcel.addEventListener('click', () => {
    window.location.href = facturaService.urlReporte('excel', filtrosAplicados);
});

btnPaginaAnterior.addEventListener('click', () => cargarFacturas(paginaActual - 1));
btnPaginaSiguiente.addEventListener('click', () => cargarFacturas(paginaActual + 1));
tamanioPagina.addEventListener('change', () => cargarFacturas(0));

ordenFecha.addEventListener('change', () => {
    filtrosAplicados = { ...filtrosAplicados, ordenFecha: ordenFecha.value === 'ASC' ? 'ASC' : 'DESC' };
    void cargarFacturas(0);
});
altaTipo.addEventListener('change', sincronizarTipo);
altaLista.addEventListener('change', () => { void recotizarItems(); });
btnCancelarEdicion.addEventListener('click', () => {
    reiniciarFormularioAlta();
    panelAlta.open = false;
    panelAlta.querySelector('summary')?.focus();
});
btnAgregarItem.addEventListener('click', agregarItem);
btnAltaCliente.addEventListener('click', async () => {
    soloReceptoresFacturaC = true;
    let cliente: ClienteExplorerDTO | null;
    try {
        cliente = await explorerClientes.abrir();
    } finally {
        soloReceptoresFacturaC = false;
    }
    if (cliente) {
        clienteAlta = cliente;
        altaCliente.value = `${cliente.denominacion} (${cliente.cuitCuil})`;
    }
});
btnFiltroCliente.addEventListener('click', async () => {
    const cliente = await explorerClientes.abrir();
    if (cliente) {
        clienteFiltro = cliente;
        filtroCliente.value = `${cliente.denominacion} (${cliente.cuitCuil})`;
    }
});
btnLimpiarCliente.addEventListener('click', () => {
    clienteFiltro = null;
    filtroCliente.value = '';
});
formAlta.addEventListener('submit', crearFactura);

btnReintentarCatalogos.addEventListener('click', () => { void cargarCatalogos(); });

async function iniciar(): Promise<void> {
    reiniciarFormularioAlta();
    await Promise.all([cargarCatalogos(), cargarFacturas()]);
}

void iniciar();
