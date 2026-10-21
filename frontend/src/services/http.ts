import { ErrorResponseDTO } from '../models/factura.dto.js';

export const API_URL = 'http://localhost:8080/api';

/** Error de la API con el código HTTP y los mensajes que mandó el backend. */
export class ApiError extends Error {
    readonly status: number;
    readonly detalles: string[];

    constructor(status: number, mensaje: string, detalles: string[] = []) {
        super(mensaje);
        this.name = 'ApiError';
        this.status = status;
        this.detalles = detalles;
    }
}

/**
 * Hace la petición y devuelve el JSON ya tipado.
 * Si la respuesta no es 2xx, lanza un ApiError con el mensaje del backend.
 */
export async function pedir<T>(url: string, opciones: RequestInit = {}): Promise<T> {
    let respuesta: Response;

    try {
        respuesta = await fetch(url, {
            ...opciones,
            // Content-Type solo cuando mandamos un cuerpo (POST/PUT)
            headers: opciones.body ? { 'Content-Type': 'application/json' } : undefined
        });
    } catch {
        // fetch solo "falla" si no hubo respuesta: servidor caído, CORS, sin red
        throw new ApiError(0, 'No se pudo conectar con el servidor. ¿Está corriendo la API en el puerto 8080?');
    }

    if (respuesta.status === 204) {
        return undefined as T;   // No Content: no hay cuerpo que leer
    }

    const cuerpo: unknown = await respuesta.json().catch(() => null);

    if (!respuesta.ok) {
        const error = cuerpo as ErrorResponseDTO | null;
        throw new ApiError(
            respuesta.status,
            error?.mensaje ?? `Error HTTP ${respuesta.status}`,
            error?.detalles ?? []
        );
    }

    return cuerpo as T;
}

/** Arma "?clave=valor&..." omitiendo los valores vacíos. */
export function construirQueryString(parametros: object): string {
    const params = new URLSearchParams();
    for (const [clave, valor] of Object.entries(parametros)) {
        if (valor !== undefined && valor !== null && String(valor).trim() !== '') {
            params.append(clave, String(valor));
        }
    }
    const query = params.toString();
    return query ? `?${query}` : '';
}