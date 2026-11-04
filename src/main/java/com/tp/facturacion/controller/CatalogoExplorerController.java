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

    public CatalogoExplorerController(CatalogoExplorerService service) {
        this.service = service;
    }

    @GetMapping("/clientes/explorer")
    public PaginaDto<ClienteExplorerDto> clientes(@RequestParam(defaultValue = "") String busqueda,
                                                 @Valid @ModelAttribute PaginacionDto paginacion) {
        return service.clientes(busqueda, paginacion);
    }

    @GetMapping("/listas-precio-articulos/explorer")
    public PaginaDto<ListaPrecioArticuloExplorerDto> listasPrecioArticulos(
            @RequestParam(defaultValue = "") String busqueda,
            @Valid @ModelAttribute PaginacionDto paginacion) {
        return service.listasPrecioArticulos(busqueda, paginacion);
    }
}
