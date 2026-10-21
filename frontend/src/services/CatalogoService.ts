import { ArticuloOpcionDTO, OpcionDTO } from '../models/factura.dto.js';
import { API_URL, pedir } from './http.js';

/** Datos de referencia para cargar los <select> del formulario de alta. */
export class CatalogoService {

    private readonly baseUrl = `${API_URL}/catalogos`;

    obtenerPuntosVenta(): Promise<OpcionDTO[]> {
        return pedir<OpcionDTO[]>(`${this.baseUrl}/puntos-venta`);
    }

    obtenerClientes(): Promise<OpcionDTO[]> {
        return pedir<OpcionDTO[]>(`${this.baseUrl}/clientes`);
    }

    obtenerCondicionesIva(): Promise<OpcionDTO[]> {
        return pedir<OpcionDTO[]>(`${this.baseUrl}/condiciones-iva`);
    }

    obtenerMonedas(): Promise<OpcionDTO[]> {
        return pedir<OpcionDTO[]>(`${this.baseUrl}/monedas`);
    }

    obtenerArticulos(): Promise<ArticuloOpcionDTO[]> {
        return pedir<ArticuloOpcionDTO[]>(`${this.baseUrl}/articulos`);
    }
}