import { ArticuloOpcionDTO, ClienteExplorerDTO, ListaPrecioArticuloExplorerDTO, OpcionDTO, PaginaDTO } from '../models/factura.dto.js';
import { API_URL, construirQueryString, pedir } from './http.js';

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

    buscarClientes(busqueda = '', pagina = 0): Promise<PaginaDTO<ClienteExplorerDTO>> {
        return pedir(`${this.baseUrl}/clientes/explorer${construirQueryString({busqueda, pagina, tamanio: 10})}`);
    }

    buscarListasPrecioArticulos(busqueda = '', pagina = 0): Promise<PaginaDTO<ListaPrecioArticuloExplorerDTO>> {
        return pedir(`${this.baseUrl}/listas-precio-articulos/explorer${construirQueryString({busqueda, pagina, tamanio: 10})}`);
    }
}
