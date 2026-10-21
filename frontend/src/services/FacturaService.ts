import {
    EstadoFactura,
    FacturaFiltrosDTO,
    FacturaReporteDTO,
    FacturaRequestDTO
} from '../models/factura.dto.js';
import { API_URL, construirQueryString, pedir } from './http.js';

/** Centraliza todas las llamadas HTTP a /api/facturas. */
export class FacturaService {

    private readonly baseUrl = `${API_URL}/facturas`;

    /** GET /api/facturas?filtros → listado */
    obtenerFacturas(filtros: FacturaFiltrosDTO = {}): Promise<FacturaReporteDTO[]> {
        return pedir<FacturaReporteDTO[]>(`${this.baseUrl}${construirQueryString(filtros)}`);
    }

    /** POST /api/facturas → 201 con la factura creada */
    crearFactura(factura: FacturaRequestDTO): Promise<FacturaReporteDTO> {
        return pedir<FacturaReporteDTO>(this.baseUrl, {
            method: 'POST',
            body: JSON.stringify(factura)
        });
    }

    /** PUT /api/facturas/{id} con solo estado (y observaciones) → 200 */
    actualizarEstado(id: number, estado: EstadoFactura, observaciones?: string): Promise<FacturaReporteDTO> {
        const cuerpo: FacturaRequestDTO = { estado };
        if (observaciones !== undefined && observaciones.trim() !== '') {
            cuerpo.observaciones = observaciones.trim();
        }
        return pedir<FacturaReporteDTO>(`${this.baseUrl}/${id}`, {
            method: 'PUT',
            body: JSON.stringify(cuerpo)
        });
    }

    /** DELETE /api/facturas/{id} → 204 */
    async eliminarFactura(id: number): Promise<void> {
        await pedir<void>(`${this.baseUrl}/${id}`, { method: 'DELETE' });
    }

    /** URL de descarga del reporte con los mismos filtros del listado */
    urlReporte(formato: 'pdf' | 'excel', filtros: FacturaFiltrosDTO = {}): string {
        return `${this.baseUrl}/reporte/${formato}${construirQueryString(filtros)}`;
    }
}