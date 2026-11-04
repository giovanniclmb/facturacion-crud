import { PaginaDTO } from '../models/factura.dto.js';

/** Descarta respuestas de una búsqueda anterior o de una ventana ya cerrada. */
export class ConsultaExplorer<T> {
    private version = 0;

    constructor(private readonly consultar: (texto: string, pagina: number) => Promise<PaginaDTO<T>>) {}

    cancelar(): void {
        this.version++;
    }

    async buscar(texto: string, pagina: number): Promise<PaginaDTO<T> | undefined> {
        const version = ++this.version;
        try {
            const resultado = await this.consultar(texto, pagina);
            return version === this.version ? resultado : undefined;
        } catch (error) {
            if (version === this.version) throw error;
            return undefined;
        }
    }
}
