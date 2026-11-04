export type EstadoFiscal = 'PREPARANDO' | 'ENVIANDO' | 'INCIERTO' | 'AUTORIZADO' | 'RECHAZADO' | 'FALLIDO_ANTES_ENVIO';
export interface ErrorFiscalDTO { codigo: number; mensaje: string; }
export interface FacturaArcaDTO {
    facturaId: number;
    numeroLocal: number | null;
    intentoId: string | null;
    estadoFiscal: EstadoFiscal | null;
    numeroFiscal: number | null;
    puntoVenta: number;
    tipoComprobante: number;
    ambiente: string;
    cae: string | null;
    caeVencimiento: string | null;
    mensaje: string;
    errores: ErrorFiscalDTO[];
    observaciones: ErrorFiscalDTO[];
    puedeAutorizar: boolean;
    puedeConsultar: boolean;
    puedeModificar: boolean;
    puedeAnular: boolean;
    puedeEliminar: boolean;
}
