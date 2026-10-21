// Espejo de los DTOs de Spring Boot: mismos nombres de campos, mismos tipos.

/** Estados posibles de una factura (en Java es un String; acá lo restringimos). */
export type EstadoFactura = 'PENDIENTE' | 'EMITIDA' | 'ANULADA';

/** Espejo de FacturaReporteDto (lo que devuelve GET, POST y PUT). */
export interface FacturaReporteDTO {
    id: number;
    numeroFactura: number | null;
    fechaEmision: string;            // "yyyy-MM-dd" (por el @JsonFormat de Java)
    clienteDenominacion: string;
    condicionIva: string;
    puntoVentaDescripcion: string;
    importeTotal: number;
    cantidadItems: number;
    estado: EstadoFactura;
    cae: string | null;
}

/** Espejo de FacturaDetalleRequestDto (un ítem en POST/PUT). */
export interface FacturaDetalleRequestDTO {
    articuloId: number;
    cantidad: number;
    porcentajeBonificacion?: number;
    descripcion?: string;
}

/**
 * Espejo de FacturaRequestDto (cuerpo de POST y PUT).
 * Todo es opcional porque el PUT acepta cambios parciales;
 * el backend exige cabecera e ítems solo en el alta.
 */
export interface FacturaRequestDTO {
    puntoVentaId?: number;
    clienteId?: number;
    condicionIvaId?: number;
    tipoMonedaId?: number;
    fechaEmision?: string;           // "yyyy-MM-dd"
    observaciones?: string;
    estado?: EstadoFactura;
    detalles?: FacturaDetalleRequestDTO[];
}

/** Espejo de FacturaFiltroDto (parámetros de GET /api/facturas). */
export interface FacturaFiltrosDTO {
    fechaDesde?: string;             // "yyyy-MM-dd"
    fechaHasta?: string;
    estado?: EstadoFactura;
    montoMinimo?: number;
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