// Espejo de los DTOs de Spring Boot: mismos nombres de campos, mismos tipos.

/** Estados posibles de una factura (en Java es un String; acá lo restringimos). */
export type TipoComprobante = 'FACTURA_C' | 'FACTURA_B' | 'FACTURA_X';
type TipoComprobantePersistido = TipoComprobante | 'CONSUMIDOR_FINAL';

export function normalizarTipoComprobante(tipo?: TipoComprobantePersistido | null): TipoComprobante {
    return tipo === 'CONSUMIDOR_FINAL' ? 'FACTURA_X' : tipo ?? 'FACTURA_C';
}

export type EstadoFactura = 'PENDIENTE' | 'EMITIDA' | 'ANULADA';

/** Espejo de FacturaReporteDto (lo que devuelve GET, POST y PUT). */
export interface FacturaReporteDTO {
    id: number;
    numeroFactura: number | null;
    revision?: number;
    tipoComprobante?: TipoComprobantePersistido | null;
    puedeImprimir?: boolean;
    fechaEmision: string;            // "yyyy-MM-dd" (por el @JsonFormat de Java)
    clienteDenominacion: string;
    condicionIva: string;
    puntoVentaDescripcion: string;
    importeTotal: number;
    cantidadItems: number;
    estado: EstadoFactura;
    cae: string | null;
    estadoFiscal: import('./arca.dto.js').EstadoFiscal | null;
    numeroFiscal: number | null;
    puntoVentaFiscal: number | null;
    puntoVentaNumero: number | null;
    tipoComprobanteFiscal: number | null;
    caeVencimiento: string | null;
    mensajeArca: string | null;
    puedeAutorizar: boolean;
    puedeConsultar: boolean;
    puedeModificar: boolean;
    puedeAnular: boolean;
    puedeEliminar: boolean;
}

/** Espejo de FacturaDetalleRequestDto (un ítem en POST/PUT). */
export interface FacturaDetalleRequestDTO {
    articuloId: number;
    listaPrecioArticuloId?: number;
    cantidad: number;
    porcentajeBonificacion?: number;
    descripcion?: string;
}

/**
 * Espejo de FacturaRequestDto (cuerpo de POST y PUT).
 * Todo es opcional porque el PUT acepta cambios parciales;
 * el backend exige cabecera e ítems solo en el alta.
 */
export interface FacturaEdicionDTO {
    id: number;
    revision: number;
    tipoComprobante: TipoComprobantePersistido | null;
    puntoVentaId: number;
    clienteId: number | null;
    clienteDenominacion: string | null;
    clienteCuit: string | null;
    documentoReceptorTipo: number | null;
    documentoReceptorNumero: number | null;
    condicionIvaId: number;
    tipoMonedaId: number;
    listaPrecioId: number | null;
    fechaEmision: string;
    observaciones: string | null;
    puedeModificar: boolean;
    puedeCambiarTipo: boolean;
    puntosVentaHomologacion: OpcionDTO[];
    detalles: (Omit<ListaPrecioArticuloExplorerDTO, 'id'> & {
        listaPrecioArticuloId: number;
        descripcion: string | null;
        cantidad: number;
        porcentajeBonificacion: number;
    })[];
}

export interface FacturaRequestDTO {
    tipoComprobante?: TipoComprobante;
    listaPrecioId?: number;
    revision?: number;
    operacionGuardado?: string;
    puntoVentaId?: number;
    clienteId?: number;
    documentoReceptorTipo?: number;
    documentoReceptorNumero?: number;
    condicionIvaId?: number;
    tipoMonedaId?: number;
    fechaEmision?: string;           // "yyyy-MM-dd"
    observaciones?: string;
    estado?: EstadoFactura;
    detalles?: FacturaDetalleRequestDTO[];
}

/** Espejo de FacturaFiltroDto (parámetros de GET /api/facturas). */
export interface FacturaFiltrosDTO {
    ordenFecha?: 'ASC' | 'DESC';
    numeroFactura?: number;
    clienteId?: number;
    sinInformarArca?: boolean;
    fechaDesde?: string;             // "yyyy-MM-dd"
    fechaHasta?: string;
    estado?: EstadoFactura;
    montoMinimo?: number;
}

/** Página de resultados; pagina comienza en cero en la API. */
export interface PaginaDTO<T> {
    contenido: T[];
    pagina: number;
    tamanio: number;
    totalElementos: number;
    totalPaginas: number;
}

/** Espejo de ErrorResponse (cuerpo de cualquier respuesta de error). */
export interface ErrorResponseDTO {
    status: number;
    mensaje: string;
    detalles: string[];
    fecha: string;
}

/** Espejo de OpcionDto (catálogos para los <select>). */
export interface OpcionDTO {
    id: number;
    descripcion: string;
}

/** Espejo de ArticuloOpcionDto (catálogo de artículos con su precio vigente). */
export interface ArticuloOpcionDTO extends OpcionDTO {
    precio: number;
}

export interface ClienteExplorerDTO {
    id: number;
    denominacion: string;
    cuitCuil: string;
}

export interface ListaPrecioArticuloExplorerDTO {
    id: number;
    articuloId: number;
    articuloCodigo: string;
    articuloDenominacion: string;
    listaPrecioCodigo: string;
    listaPrecioDenominacion: string;
    precioVenta: number;
    ivaPorcentaje?: number | null;
}
