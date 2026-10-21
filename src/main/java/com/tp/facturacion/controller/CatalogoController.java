package com.tp.facturacion.controller;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tp.facturacion.dto.ArticuloOpcionDto;
import com.tp.facturacion.dto.OpcionDto;
import com.tp.facturacion.model.ListaPrecioArticulo;
import com.tp.facturacion.repository.ClienteRepository;
import com.tp.facturacion.repository.CondicionIvaRepository;
import com.tp.facturacion.repository.ListaPrecioArticuloRepository;
import com.tp.facturacion.repository.PuntoVentaRepository;
import com.tp.facturacion.repository.TipoMonedaRepository;

@RestController
@RequestMapping("/api/catalogos")
public class CatalogoController {

    private final PuntoVentaRepository puntoVentaRepository;
    private final ClienteRepository clienteRepository;
    private final CondicionIvaRepository condicionIvaRepository;
    private final TipoMonedaRepository tipoMonedaRepository;
    private final ListaPrecioArticuloRepository listaPrecioArticuloRepository;

    public CatalogoController(PuntoVentaRepository puntoVentaRepository,
                              ClienteRepository clienteRepository,
                              CondicionIvaRepository condicionIvaRepository,
                              TipoMonedaRepository tipoMonedaRepository,
                              ListaPrecioArticuloRepository listaPrecioArticuloRepository) {
        this.puntoVentaRepository = puntoVentaRepository;
        this.clienteRepository = clienteRepository;
        this.condicionIvaRepository = condicionIvaRepository;
        this.tipoMonedaRepository = tipoMonedaRepository;
        this.listaPrecioArticuloRepository = listaPrecioArticuloRepository;
    }

    @GetMapping("/puntos-venta")
    public List<OpcionDto> puntosVenta() {
        return puntoVentaRepository.findAll(Sort.by("numero")).stream()
                .filter(pv -> pv.getFechaBaja() == null)
                .map(pv -> new OpcionDto(pv.getId(), pv.getNumero() + " - " + pv.getDescripcion()))
                .toList();
    }

    @GetMapping("/clientes")
    public List<OpcionDto> clientes() {
        return clienteRepository.findAll(Sort.by("denominacion")).stream()
                .filter(c -> c.getFechaBaja() == null)
                .map(c -> new OpcionDto(c.getId(), c.getDenominacion() + " (" + c.getCuitCuil() + ")"))
                .toList();
    }

    @GetMapping("/condiciones-iva")
    public List<OpcionDto> condicionesIva() {
        return condicionIvaRepository.findAll(Sort.by("denominacion")).stream()
                .filter(ci -> ci.getFechaBaja() == null)
                .map(ci -> new OpcionDto(ci.getId(), ci.getDenominacion()))
                .toList();
    }

    @GetMapping("/monedas")
    public List<OpcionDto> monedas() {
        return tipoMonedaRepository.findAll(Sort.by("denominacion")).stream()
                .filter(m -> m.getFechaBaja() == null)
                .map(m -> new OpcionDto(m.getId(), m.getDenominacion() + " (" + m.getSimbolo() + ")"))
                .toList();
    }

    /** Artículos con su precio vigente: misma regla que usa FacturaService al facturar. */
    @GetMapping("/articulos")
    public List<ArticuloOpcionDto> articulos() {
        Map<Long, ListaPrecioArticulo> vigentes = new LinkedHashMap<>();
        for (ListaPrecioArticulo lpa : listaPrecioArticuloRepository.findAll(Sort.by("id"))) {
            if (lpa.getFechaBaja() == null) {
                vigentes.put(lpa.getArticulo().getId(), lpa); // si hay varios, el último pisa al anterior
            }
        }
        return vigentes.values().stream()
                .map(lpa -> new ArticuloOpcionDto(
                        lpa.getArticulo().getId(),
                        lpa.getArticulo().getDenominacion(),
                        lpa.getPrecioVenta()))
                .sorted(Comparator.comparing(ArticuloOpcionDto::descripcion))
                .toList();
    }
}