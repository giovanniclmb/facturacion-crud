import { FacturaReporteDTO } from './factura.dto.js';

type FacturaPermisos = Pick<FacturaReporteDTO, 'estado' | 'cae'> & Partial<FacturaReporteDTO>;

/** Los permisos explícitos y el bloqueo fiscal prevalecen sobre los valores heredados. */
export function permisosFactura(factura: FacturaPermisos): {
    modificar: boolean; anular: boolean; eliminar: boolean;
    autorizar: boolean; consultar: boolean; motivo: string;
} {
    const autorizada = Boolean(factura.cae?.trim()) || factura.estadoFiscal === 'AUTORIZADO';
    const pendiente = ['PREPARANDO', 'ENVIANDO', 'INCIERTO'].includes(factura.estadoFiscal ?? '');
    const bloqueada = autorizada || pendiente;
    return {
        modificar: !bloqueada && factura.puedeModificar !== false,
        anular: !bloqueada && factura.estado !== 'ANULADA' && factura.puedeAnular !== false,
        eliminar: !bloqueada && factura.puedeEliminar !== false,
        autorizar: !bloqueada && factura.estado !== 'ANULADA' && factura.puedeAutorizar === true,
        consultar: factura.puedeConsultar === true,
        motivo: autorizada ? 'Factura autorizada por ARCA: no puede modificarse, anularse ni eliminarse.'
            : pendiente ? 'Resultado fiscal pendiente: consulte ARCA antes de modificar la factura.'
            : factura.estado === 'ANULADA' ? 'La factura ya está anulada.'
            : 'Acción no permitida por el estado de la factura.'
    };
}
