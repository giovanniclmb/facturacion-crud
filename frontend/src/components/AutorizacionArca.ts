import { FacturaReporteDTO } from '../models/factura.dto.js';
import { FacturaArcaDTO } from '../models/arca.dto.js';
import { permisosFactura } from '../models/permisosFactura.js';
import { FacturaService } from '../services/FacturaService.js';
import { ApiError } from '../services/http.js';

interface CoordinacionArca {
    bloqueada(): boolean;
    recuperacionPendiente(): boolean;
    iniciar(): void;
    finalizar(): void;
    bloquearRecuperacion(bloquear: boolean): void;
    estadoActualizado(estado: FacturaArcaDTO): void;
}

/** No consulta al construir: la página del backend ya incluye estado y permisos. */
export class AutorizacionArca {
    readonly acciones = document.createElement('div');
    readonly estado = document.createElement('div');
    private readonly confirmar = document.createElement('input');
    private readonly etiqueta = document.createElement('label');
    private readonly informar = this.boton('Informar a ARCA', 'btn-informar-arca');
    private readonly consultar = this.boton('Consultar ARCA', 'btn-consultar-arca');
    private readonly recargar = this.boton('Recargar estado ARCA', 'btn-recargar-arca');
    private ocupado = false;
    private recuperacionFallida = false;
    private detalle?: FacturaArcaDTO;

    constructor(private readonly factura: FacturaReporteDTO, private readonly servicio: FacturaService,
                private readonly coordinacion: CoordinacionArca) {
        for (const boton of [this.informar, this.consultar, this.recargar]) {
            boton.setAttribute('aria-label', `${boton.textContent}, factura N° ${factura.numeroFactura}, ${factura.puntoVentaDescripcion}`);
        }
        this.acciones.className = 'arca-acciones';
        this.estado.className = 'arca-estado fila-secundaria';
        this.estado.setAttribute('aria-live', 'polite');
        this.estado.setAttribute('aria-atomic', 'true');
        this.estado.tabIndex = -1;
        this.confirmar.type = 'checkbox';
        this.etiqueta.className = 'arca-confirmacion';
        this.etiqueta.append(this.confirmar, document.createTextNode('Homologación. Confirmo que el documento del cliente es un CUIT.'));
        this.confirmar.addEventListener('change', () => this.actualizar());
        this.informar.addEventListener('click', () => { void this.ejecutar('autorizar'); });
        this.consultar.addEventListener('click', () => { void this.ejecutar('consultar'); });
        this.recargar.addEventListener('click', () => { void this.ejecutar('estado'); });
        this.acciones.append(this.etiqueta, this.informar, this.consultar, this.recargar);
        this.actualizar();
    }

    private boton(texto: string, clase: string): HTMLButtonElement {
        const boton = document.createElement('button');
        boton.type = 'button';
        boton.textContent = texto;
        boton.className = `btn btn-outline-primary ${clase}`;
        return boton;
    }

    actualizarDatos(estado: FacturaArcaDTO): void {
        this.detalle = estado;
        Object.assign(this.factura, estado);
        this.actualizar();
    }

    actualizar(): void {
        if (!this.ocupado && this.coordinacion.recuperacionPendiente()) {
            this.recuperacionFallida = true;
            this.estado.textContent = 'No se pudo verificar el resultado fiscal. Las acciones siguen bloqueadas. Recargue el estado ARCA; no vuelva a informar.';
        }
        const permisos = permisosFactura(this.factura);
        const bloqueada = this.ocupado || this.coordinacion.bloqueada();
        this.informar.hidden = !permisos.autorizar;
        this.etiqueta.hidden = !permisos.autorizar;
        this.informar.disabled = bloqueada || !permisos.autorizar || !this.confirmar.checked;
        this.confirmar.disabled = bloqueada;
        this.consultar.hidden = !permisos.consultar;
        this.consultar.disabled = bloqueada || !permisos.consultar;
        this.recargar.hidden = !this.recuperacionFallida && !this.factura.estadoFiscal;
        const textoLectura = this.recuperacionFallida ? 'Recargar estado ARCA' : 'Ver estado ARCA';
        this.recargar.textContent = textoLectura;
        this.recargar.setAttribute('aria-label', `${textoLectura}, factura N° ${this.factura.numeroFactura}, ${this.factura.puntoVentaDescripcion}`);
        // La lectura permite recuperar incluso cuando las mutaciones están bloqueadas.
        this.recargar.disabled = this.ocupado || (!this.recuperacionFallida && this.coordinacion.bloqueada());
        this.acciones.setAttribute('aria-busy', String(this.ocupado));
        if (!this.ocupado && !this.recuperacionFallida) this.mostrarEstado();
    }

    private mostrarEstado(): void {
        const f = this.factura;
        const fragmentos = [f.estadoFiscal ?? (f.cae?.trim() ? 'AUTORIZADO' : 'Sin informar')];
        const permisos = permisosFactura(f);
        if (!permisos.anular && !permisos.eliminar) fragmentos.push('Solo lectura');
        else if (!permisos.eliminar) fragmentos.push('Historial fiscal: borrado bloqueado');
        if (f.numeroFiscal != null) fragmentos.push(`N° fiscal ${f.numeroFiscal}`);
        if (this.detalle) fragmentos.push(`${this.detalle.ambiente} · Punto ${this.detalle.puntoVenta} · Tipo ${this.detalle.tipoComprobante}`);
        fragmentos.push(f.cae?.trim() ? `CAE ${f.cae.trim()}` : 'Sin CAE');
        if (f.caeVencimiento) fragmentos.push(`Vencimiento CAE ${f.caeVencimiento}`);
        if (this.detalle?.mensaje) fragmentos.push(this.detalle.mensaje);
        else if (!this.detalle && f.mensajeArca) fragmentos.push(f.mensajeArca);
        for (const error of this.detalle?.errores ?? []) fragmentos.push(`${error.codigo}: ${error.mensaje}`);
        for (const obs of this.detalle?.observaciones ?? []) fragmentos.push(`Observación ${obs.codigo}: ${obs.mensaje}`);
        this.estado.textContent = fragmentos.join(' · ');
    }

    private async ejecutar(operacion: 'autorizar' | 'consultar' | 'estado'): Promise<void> {
        const permisos = permisosFactura(this.factura);
        if (this.ocupado || (operacion !== 'estado' && this.coordinacion.bloqueada())) return;
        if (operacion === 'autorizar' && (!permisos.autorizar || !this.confirmar.checked)) return;
        if (operacion === 'consultar' && !permisos.consultar) return;
        const conservarFoco = this.acciones.contains(document.activeElement);
        this.ocupado = true;
        this.coordinacion.iniciar();
        this.coordinacion.bloquearRecuperacion(true);
        this.estado.textContent = operacion === 'autorizar' ? 'Informando a ARCA en homologación…' : 'Consultando estado ARCA…';
        this.actualizar();
        try {
            let resultado: FacturaArcaDTO;
            try {
                resultado = operacion === 'autorizar' ? await this.servicio.autorizarArca(this.factura.id, true)
                    : operacion === 'consultar' ? await this.servicio.consultarArca(this.factura.id)
                    : await this.servicio.estadoArca(this.factura.id);
            } catch (error) {
                if (operacion === 'estado') throw error;
                // Cualquier fallo después del POST puede ocultar un resultado durable.
                resultado = await this.servicio.estadoArca(this.factura.id);
                if (error instanceof ApiError && error.status !== 0) {
                    resultado = { ...resultado, mensaje: `${resultado.mensaje} · ${error.message}` };
                }
            }
            if (!resultado || resultado.facturaId !== this.factura.id
                || !['puedeAutorizar', 'puedeConsultar', 'puedeModificar', 'puedeAnular', 'puedeEliminar']
                    .every(campo => typeof resultado[campo as keyof FacturaArcaDTO] === 'boolean')
                || ![null, 'PREPARANDO', 'ENVIANDO', 'INCIERTO', 'AUTORIZADO', 'RECHAZADO', 'FALLIDO_ANTES_ENVIO']
                    .includes(resultado.estadoFiscal)) {
                throw new Error('Estado fiscal incompleto');
            }
            this.recuperacionFallida = false;
            this.confirmar.checked = false;
            this.actualizarDatos(resultado);
            this.coordinacion.estadoActualizado(resultado);
            this.coordinacion.bloquearRecuperacion(false);
        } catch {
            this.recuperacionFallida = true;
            this.estado.textContent = 'No se pudo verificar el resultado fiscal. Las acciones siguen bloqueadas. Recargue el estado ARCA; no vuelva a informar.';
        } finally {
            this.ocupado = false;
            this.coordinacion.finalizar();
            this.actualizar();
            if (conservarFoco && this.acciones.isConnected) {
                const destino = this.recuperacionFallida ? this.recargar : !this.consultar.hidden ? this.consultar
                    : !this.etiqueta.hidden ? this.confirmar : this.estado;
                destino.focus();
            }
        }
    }
}
