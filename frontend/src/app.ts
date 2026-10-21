import {
    ArticuloOpcionDTO,
    EstadoFactura,
    FacturaDetalleRequestDTO,
    FacturaFiltrosDTO,
    FacturaReporteDTO,
    FacturaRequestDTO,
    OpcionDTO
} from './models/factura.dto.js';
import { FacturaService } from './services/FacturaService.js';
import { CatalogoService } from './services/CatalogoService.js';
import { ApiError } from './services/http.js';

// ============================================================
// Servicios y estado en memoria
// ============================================================

const facturaService = new FacturaService();
const catalogoService = new CatalogoService();

/** Un renglón del formulario de alta (lo que el usuario está cargando). */
interface ItemFormulario {
    articuloId: number | null;
    cantidad: number;
    porcentajeBonificacion: number;
}

let items: ItemFormulario[] = [];
let articulos: ArticuloOpcionDTO[] = [];

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
const filtroFechaDesde = elemento<HTMLInputElement>('filtro-fecha-desde');
const filtroFechaHasta = elemento<HTMLInputElement>('filtro-fecha-hasta');
const filtroEstado = elemento<HTMLSelectElement>('filtro-estado');
const filtroMontoMinimo = elemento<HTMLInputElement>('filtro-monto-minimo');
const btnLimpiarFiltros = elemento<HTMLButtonElement>('btn-limpiar-filtros');
const btnReportePdf = elemento<HTMLButtonElement>('btn-reporte-pdf');
const btnReporteExcel = elemento<HTMLButtonElement>('btn-reporte-excel');

// Alta
const formAlta = elemento<HTMLFormElement>('form-alta');
const altaPuntoVenta = elemento<HTMLSelectElement>('alta-punto-venta');
const altaCliente = elemento<HTMLSelectElement>('alta-cliente');
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
const listadoResumen = elemento<HTMLSpanElement>('listado-resumen');

// ============================================================
// Utilidades
// ============================================================

const formatoMoneda = new Intl.NumberFormat('es-AR', { style: 'currency', currency: 'ARS' });

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
    mensaje.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
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
    // Las cinco peticiones salen a la vez y esperamos a que terminen todas
    const [puntosVenta, clientes, condicionesIva, monedas, listaArticulos] = await Promise.all([
        catalogoService.obtenerPuntosVenta(),
        catalogoService.obtenerClientes(),
        catalogoService.obtenerCondicionesIva(),
        catalogoService.obtenerMonedas(),
        catalogoService.obtenerArticulos()
    ]);

    llenarSelect(altaPuntoVenta, puntosVenta, 'Seleccione…');
    llenarSelect(altaCliente, clientes, 'Seleccione…');
    llenarSelect(altaCondicionIva, condicionesIva, 'Seleccione…');
    llenarSelect(altaMoneda, monedas, 'Seleccione…');
    articulos = listaArticulos;
}

function precioDe(articuloId: number | null): number {
    return articulos.find(a => a.id === articuloId)?.precio ?? 0;
}

// ============================================================
// Ítems del alta (tabla dinámica)
// ============================================================

function subtotalDe(item: ItemFormulario): number {
    const bruto = item.cantidad * precioDe(item.articuloId);
    return redondear(bruto - bruto * item.porcentajeBonificacion / 100);
}

function actualizarTotal(): void {
    const total = items.reduce((suma, item) => suma + subtotalDe(item), 0);
    itemsTotal.textContent = formatearMoneda(redondear(total));
}

function crearInputNumero(valor: number, min: string, max: string | null, step: string,
                          alCambiar: (valor: number) => void): HTMLInputElement {
    const input = crearElemento('input', 'form-control form-control-sm');
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
        celdaPrecio.textContent = formatearMoneda(precioDe(item.articuloId));
        celdaSubtotal.textContent = formatearMoneda(subtotalDe(item));
        actualizarTotal();
    };

    // Artículo
    const selectArticulo = crearElemento('select', 'form-select form-select-sm');
    selectArticulo.appendChild(new Option('Seleccione…', ''));
    articulos.forEach(a => selectArticulo.appendChild(new Option(a.descripcion, String(a.id))));
    selectArticulo.value = item.articuloId !== null ? String(item.articuloId) : '';
    selectArticulo.addEventListener('change', () => {
        item.articuloId = selectArticulo.value ? Number(selectArticulo.value) : null;
        refrescarFila();
    });

    // Cantidad y bonificación
    const inputCantidad = crearInputNumero(item.cantidad, '0.01', null, '1', v => {
        item.cantidad = v;
        refrescarFila();
    });
    const inputBonificacion = crearInputNumero(item.porcentajeBonificacion, '0', '100', '0.5', v => {
        item.porcentajeBonificacion = v;
        refrescarFila();
    });

    // Quitar
    const btnQuitar = crearElemento('button', 'btn btn-sm btn-outline-danger', 'Quitar');
    btnQuitar.type = 'button';
    btnQuitar.disabled = items.length === 1; // siempre queda al menos un ítem
    btnQuitar.addEventListener('click', () => {
        items.splice(indice, 1);
        renderizarItems();
    });

    const celda = (contenido: HTMLElement): HTMLTableCellElement => {
        const td = crearElemento('td');
        td.appendChild(contenido);
        return td;
    };

    fila.append(celda(selectArticulo), celda(inputCantidad), celda(inputBonificacion),
                celdaPrecio, celdaSubtotal, celda(btnQuitar));
    refrescarFila();
    return fila;
}

function renderizarItems(): void {
    itemsTbody.replaceChildren(...items.map((item, i) => crearFilaItem(item, i)));
    actualizarTotal();
}

function agregarItem(): void {
    items.push({ articuloId: null, cantidad: 1, porcentajeBonificacion: 0 });
    renderizarItems();
}

function reiniciarFormularioAlta(): void {
    formAlta.reset();
    altaFecha.value = hoyISO();
    items = [];
    agregarItem();
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
    if (!altaPuntoVenta.value) errores.push('Seleccione el punto de venta');
    if (!altaCliente.value) errores.push('Seleccione el cliente');
    if (!altaCondicionIva.value) errores.push('Seleccione la condición de IVA');
    if (!altaMoneda.value) errores.push('Seleccione la moneda');
    items.forEach((item, i) => {
        if (item.articuloId === null) errores.push(`Ítem ${i + 1}: seleccione el artículo`);
        if (item.cantidad <= 0) errores.push(`Ítem ${i + 1}: la cantidad debe ser mayor que 0`);
        if (item.porcentajeBonificacion < 0 || item.porcentajeBonificacion > 100) {
            errores.push(`Ítem ${i + 1}: la bonificación debe estar entre 0 y 100`);
        }
    });
    return errores;
}

function leerFormularioAlta(): FacturaRequestDTO {
    const detalles: FacturaDetalleRequestDTO[] = items.map(item => ({
        articuloId: item.articuloId as number,   // validarAlta garantiza que no es null
        cantidad: item.cantidad,
        porcentajeBonificacion: item.porcentajeBonificacion
    }));

    return {
        puntoVentaId: idSeleccionado(altaPuntoVenta),
        clienteId: idSeleccionado(altaCliente),
        condicionIvaId: idSeleccionado(altaCondicionIva),
        tipoMonedaId: idSeleccionado(altaMoneda),
        fechaEmision: altaFecha.value || undefined,
        observaciones: altaObservaciones.value.trim() || undefined,
        detalles
    };
}

async function crearFactura(evento: SubmitEvent): Promise<void> {
    evento.preventDefault(); // evita que el formulario recargue la página

    const errores = validarAlta();
    if (errores.length > 0) {
        mostrarMensaje('warning', 'Revise los datos de la factura:', errores);
        return;
    }

    btnCrear.disabled = true;
    try {
        const creada = await facturaService.crearFactura(leerFormularioAlta());
        mostrarMensaje('success',
            `Factura N° ${creada.numeroFactura} creada (${formatearMoneda(creada.importeTotal)}). Estado: ${creada.estado}.`);
        reiniciarFormularioAlta();
        await cargarFacturas();
    } catch (error) {
        mostrarError(error);
    } finally {
        btnCrear.disabled = false;
    }
}

// ============================================================
// Listado y acciones por fila
// ============================================================

function leerFiltros(): FacturaFiltrosDTO {
    return {
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
    if (f.estado === 'ANULADA') fila.classList.add('text-muted');

    const columnas: [string, string][] = [
        [f.numeroFactura !== null ? String(f.numeroFactura) : '—', ''],
        [formatearFecha(f.fechaEmision), ''],
        [f.clienteDenominacion, ''],
        [f.condicionIva, ''],
        [f.puntoVentaDescripcion, ''],
        [String(f.cantidadItems), 'numero'],
        [formatearMoneda(f.importeTotal), 'numero']
    ];
    // textContent (no innerHTML): el texto nunca se interpreta como HTML
    columnas.forEach(([texto, clase]) => fila.appendChild(crearElemento('td', clase, texto)));

    const celdaEstado = crearElemento('td');
    celdaEstado.appendChild(crearElemento('span', `badge ${CLASE_ESTADO[f.estado] ?? 'text-bg-light'}`, f.estado));
    fila.appendChild(celdaEstado);

    fila.appendChild(crearElemento('td', 'small', f.cae ?? '—'));

    // Acciones
    const celdaAcciones = crearElemento('td', 'acciones');

    const btnAnular = crearElemento('button', 'btn btn-sm btn-outline-warning me-1', 'Anular');
    btnAnular.type = 'button';
    btnAnular.disabled = f.estado === 'ANULADA';
    btnAnular.addEventListener('click', () => anularFactura(f));

    const btnEliminar = crearElemento('button', 'btn btn-sm btn-outline-danger', 'Eliminar');
    btnEliminar.type = 'button';
    if (f.cae) {
        btnEliminar.disabled = true;
        btnEliminar.title = 'Tiene CAE: no puede eliminarse, solo anularse';
    }
    btnEliminar.addEventListener('click', () => eliminarFactura(f));

    celdaAcciones.append(btnAnular, btnEliminar);
    fila.appendChild(celdaAcciones);
    return fila;
}

function renderizarFacturas(facturas: FacturaReporteDTO[]): void {
    if (facturas.length === 0) {
        const fila = crearElemento('tr');
        const celda = crearElemento('td', 'text-center text-muted py-4', 'No hay comprobantes para los filtros seleccionados');
        celda.colSpan = 10;
        fila.appendChild(celda);
        facturasTbody.replaceChildren(fila);
    } else {
        facturasTbody.replaceChildren(...facturas.map(crearFilaFactura));
    }

    const total = facturas.reduce((suma, f) => suma + f.importeTotal, 0);
    listadoResumen.textContent = `${facturas.length} comprobante(s) · ${formatearMoneda(total)}`;
}

async function cargarFacturas(): Promise<void> {
    try {
        renderizarFacturas(await facturaService.obtenerFacturas(leerFiltros()));
    } catch (error) {
        mostrarError(error);
    }
}

async function anularFactura(f: FacturaReporteDTO): Promise<void> {
    const motivo = prompt(`Motivo de la anulación de la factura N° ${f.numeroFactura}:`);
    if (motivo === null) return; // canceló

    try {
        await facturaService.actualizarEstado(f.id, 'ANULADA', motivo);
        mostrarMensaje('success', `Factura N° ${f.numeroFactura} anulada.`);
        await cargarFacturas();
    } catch (error) {
        mostrarError(error);
    }
}

async function eliminarFactura(f: FacturaReporteDTO): Promise<void> {
    if (!confirm(`¿Eliminar definitivamente la factura N° ${f.numeroFactura}? Esta acción no se puede deshacer.`)) {
        return;
    }

    try {
        await facturaService.eliminarFactura(f.id);
        mostrarMensaje('success', `Factura N° ${f.numeroFactura} eliminada.`);
        await cargarFacturas();
    } catch (error) {
        mostrarError(error);
    }
}

// ============================================================
// Eventos e inicio
// ============================================================

formFiltros.addEventListener('submit', evento => {
    evento.preventDefault();
    ocultarMensaje();
    cargarFacturas();
});

btnLimpiarFiltros.addEventListener('click', () => {
    formFiltros.reset();
    ocultarMensaje();
    cargarFacturas();
});

btnReportePdf.addEventListener('click', () => {
    window.location.href = facturaService.urlReporte('pdf', leerFiltros());
});

btnReporteExcel.addEventListener('click', () => {
    window.location.href = facturaService.urlReporte('excel', leerFiltros());
});

btnAgregarItem.addEventListener('click', agregarItem);
formAlta.addEventListener('submit', crearFactura);

async function iniciar(): Promise<void> {
    try {
        await cargarCatalogos();
    } catch (error) {
        mostrarError(error);
    }
    reiniciarFormularioAlta();
    await cargarFacturas();
}

iniciar();