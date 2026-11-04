package com.tp.facturacion.dto;

import java.util.List;

public record PaginaDto<T>(List<T> contenido, int pagina, int tamanio,
                           long totalElementos, long totalPaginas) {
}
