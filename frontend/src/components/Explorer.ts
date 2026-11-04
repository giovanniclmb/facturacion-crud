import { PaginaDTO } from '../models/factura.dto.js';
import { ConsultaExplorer } from './ConsultaExplorer.js';

interface Columna<T> {
    titulo: string;
    valor: (registro: T) => string;
    numero?: boolean;
}

interface OpcionesExplorer<T> {
    id: string;
    titulo: string;
    ayuda: string;
    columnas: Columna<T>[];
    describir: (registro: T) => string;
    buscar: (texto: string, pagina: number) => Promise<PaginaDTO<T>>;
}

function crear<K extends keyof HTMLElementTagNameMap>(etiqueta: K, clase = '', texto = ''): HTMLElementTagNameMap[K] {
    const elemento = document.createElement(etiqueta);
    elemento.className = clase;
    elemento.textContent = texto;
    return elemento;
}

/** Ventana reutilizable. El dialog nativo mantiene el foco y permite cancelar con Escape. */
export class Explorer<T> {
    private readonly dialogo = crear('dialog', 'explorer');
    private readonly campo = crear('input', 'form-control');
    private readonly cuerpo = crear('tbody');
    private readonly resumen = crear('p', 'small text-muted mb-0');
    private readonly error = crear('p', 'alert alert-danger d-none');
    private readonly anterior = crear('button', 'btn btn-outline-secondary', 'Anterior');
    private readonly siguiente = crear('button', 'btn btn-outline-secondary', 'Siguiente');
    private readonly tabla = crear('table', 'table table-hover align-middle mb-0');
    private readonly consulta: ConsultaExplorer<T>;
    private pagina = 0;
    private textoAplicado = '';
    private seleccionado: T | null = null;
    private resolver?: (registro: T | null) => void;

    constructor(private readonly opciones: OpcionesExplorer<T>) {
        this.consulta = new ConsultaExplorer(opciones.buscar);
        this.dialogo.id = opciones.id;
        this.dialogo.setAttribute('aria-labelledby', `${opciones.id}-titulo`);
        this.dialogo.setAttribute('aria-describedby', `${opciones.id}-ayuda`);
        const contenido = crear('div', 'explorer-contenido');
        const cabecera = crear('div', 'd-flex justify-content-between align-items-center gap-3 p-3 border-bottom');
        const titulo = crear('h2', 'h5 mb-0', opciones.titulo);
        titulo.id = `${opciones.id}-titulo`;
        const cerrar = crear('button', 'btn btn-outline-secondary', 'Cancelar');
        cerrar.type = 'button';
        cerrar.addEventListener('click', () => this.dialogo.close());
        cabecera.append(titulo, cerrar);

        const formulario = crear('form', 'p-3 border-bottom');
        const ayuda = crear('p', 'small text-muted', opciones.ayuda);
        ayuda.id = `${opciones.id}-ayuda`;
        const etiqueta = crear('label', 'form-label', 'Buscar');
        this.campo.type = 'search';
        this.campo.maxLength = 100;
        this.campo.id = `${opciones.id}-busqueda`;
        etiqueta.htmlFor = this.campo.id;
        const controles = crear('div', 'd-flex gap-2');
        const buscar = crear('button', 'btn btn-primary', 'Buscar');
        buscar.type = 'submit';
        controles.append(this.campo, buscar);
        formulario.append(ayuda, etiqueta, controles);
        formulario.addEventListener('submit', evento => {
            evento.preventDefault();
            this.textoAplicado = this.campo.value.trim();
            void this.cargar(0);
        });

        const scroll = crear('div', 'table-responsive explorer-resultados');
        scroll.tabIndex = 0;
        scroll.setAttribute('role', 'region');
        scroll.setAttribute('aria-label', `Resultados de ${opciones.titulo.toLowerCase()}, tabla desplazable`);
        const ayudaTabla = crear('p', 'ayuda-tabla', 'La tabla de resultados se puede desplazar horizontalmente.');
        this.tabla.setAttribute('aria-label', opciones.titulo);
        const encabezado = crear('thead', 'table-light');
        const fila = crear('tr');
        for (const columna of opciones.columnas) {
            const celda = crear('th', columna.numero ? 'numero' : '', columna.titulo);
            celda.scope = 'col';
            fila.append(celda);
        }
        const accion = crear('th', '', 'Acción');
        accion.scope = 'col';
        fila.append(accion);
        encabezado.append(fila);
        this.tabla.append(encabezado, this.cuerpo);
        scroll.append(this.tabla);
        this.error.setAttribute('role', 'alert');
        const pie = crear('div', 'd-flex flex-wrap justify-content-between align-items-center gap-3 p-3 border-top');
        this.resumen.setAttribute('role', 'status');
        this.resumen.setAttribute('aria-live', 'polite');
        const navegacion = crear('nav', 'd-flex gap-2');
        navegacion.setAttribute('aria-label', `Paginación de ${opciones.titulo.toLowerCase()}`);
        this.anterior.type = this.siguiente.type = 'button';
        this.anterior.addEventListener('click', () => { void this.cargar(this.pagina - 1); });
        this.siguiente.addEventListener('click', () => { void this.cargar(this.pagina + 1); });
        navegacion.append(this.anterior, this.siguiente);
        pie.append(this.resumen, navegacion);
        contenido.append(cabecera, formulario, this.error, ayudaTabla, scroll, pie);
        this.dialogo.append(contenido);
        document.body.append(this.dialogo);
        this.dialogo.addEventListener('close', () => {
            this.consulta.cancelar();
            const resolver = this.resolver;
            this.resolver = undefined;
            resolver?.(this.seleccionado);
        });
    }

    abrir(): Promise<T | null> {
        if (this.dialogo.open) return Promise.resolve(null);
        this.seleccionado = null;
        this.textoAplicado = this.campo.value = '';
        const resultado = new Promise<T | null>(resolver => { this.resolver = resolver; });
        this.dialogo.showModal();
        this.campo.focus();
        void this.cargar(0);
        return resultado;
    }

    private async cargar(pagina: number): Promise<void> {
        this.anterior.disabled = this.siguiente.disabled = true;
        this.tabla.setAttribute('aria-busy', 'true');
        this.error.classList.add('d-none');
        this.resumen.textContent = 'Buscando…';
        this.mostrarFilaMensaje('Cargando resultados…');
        try {
            const resultado = await this.consulta.buscar(this.textoAplicado, pagina);
            if (!resultado) return;
            this.pagina = resultado.pagina;
            this.cuerpo.replaceChildren();
            for (const registro of resultado.contenido) {
                const fila = crear('tr');
                for (const columna of this.opciones.columnas) {
                    fila.append(crear('td', columna.numero ? 'numero' : '', columna.valor(registro)));
                }
                const boton = crear('button', 'btn btn-outline-primary', 'Seleccionar');
                boton.type = 'button';
                boton.setAttribute('aria-label', `Seleccionar ${this.opciones.describir(registro)}`);
                boton.addEventListener('click', () => {
                    this.seleccionado = registro;
                    this.dialogo.close();
                });
                const acciones = crear('td');
                acciones.append(boton);
                fila.append(acciones);
                this.cuerpo.append(fila);
            }
            if (resultado.contenido.length === 0) this.mostrarFilaMensaje('No hay resultados para esta búsqueda.');
            this.resumen.textContent = resultado.totalPaginas > 0
                ? `${resultado.totalElementos} resultados · Página ${resultado.pagina + 1} de ${resultado.totalPaginas}`
                : 'Sin resultados';
            this.anterior.disabled = resultado.pagina <= 0;
            this.siguiente.disabled = resultado.pagina + 1 >= resultado.totalPaginas;
            this.tabla.setAttribute('aria-busy', 'false');
        } catch (error) {
            this.tabla.setAttribute('aria-busy', 'false');
            this.mostrarFilaMensaje('No se pudieron cargar los resultados.');
            this.resumen.textContent = 'Vuelva a buscar para reintentar.';
            this.error.textContent = error instanceof Error ? error.message : 'No se pudo completar la búsqueda.';
            this.error.classList.remove('d-none');
        }
    }

    private mostrarFilaMensaje(texto: string): void {
        const celda = crear('td', 'text-center text-muted py-4', texto);
        celda.colSpan = this.opciones.columnas.length + 1;
        const fila = crear('tr');
        fila.append(celda);
        this.cuerpo.replaceChildren(fila);
    }
}
