package com.tp.facturacion.controller;

import com.tp.facturacion.dto.ClienteExplorerDto;
import com.tp.facturacion.dto.ListaPrecioArticuloExplorerDto;
import com.tp.facturacion.dto.PaginaDto;
import com.tp.facturacion.dto.PaginacionDto;
import com.tp.facturacion.service.CatalogoExplorerService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalogos")
public class CatalogoExplorerController {
    private final CatalogoExplorerService service;

    private final com.tp.facturacion.service.ListasFacturaService listas;

    public CatalogoExplorerController(CatalogoExplorerService service,
                                     com.tp.facturacion.service.ListasFacturaService listas) {
        this.listas = listas;
        this.service = service;
    }

    @GetMapping("/listas-precio")
    public java.util.List<com.tp.facturacion.dto.OpcionDto> listasPrecio() {
        return listas.activas();
    }

    @GetMapping("/listas-precio-articulos/precio")
    public ListaPrecioArticuloExplorerDto precio(@RequestParam Long articuloId, @RequestParam Long listaPrecioId) {
        return listas.cotizar(articuloId, listaPrecioId);
    }

    @GetMapping("/clientes/explorer")
    public PaginaDto<ClienteExplorerDto> clientes(@RequestParam(defaultValue = "") String busqueda,
                                                 @RequestParam(defaultValue = "false") boolean soloFacturaC,
                                                 @Valid @ModelAttribute PaginacionDto paginacion) {
        return service.clientes(busqueda, paginacion, soloFacturaC);
    }

    @GetMapping("/listas-precio-articulos/explorer")
    public PaginaDto<ListaPrecioArticuloExplorerDto> listasPrecioArticulos(
            @RequestParam(defaultValue = "") String busqueda,
            @Valid @ModelAttribute PaginacionDto paginacion, @RequestParam(required = false) Long listaPrecioId) {
        return service.listasPrecioArticulos(busqueda, paginacion, listaPrecioId);
    }
}
