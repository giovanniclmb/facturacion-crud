package com.tp.facturacion.controller;

import com.tp.facturacion.arca.ArcaDiagnosticoDto;
import com.tp.facturacion.arca.ArcaDiagnosticoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/arca")
public class ArcaController {
    private final ArcaDiagnosticoService servicio;

    public ArcaController(ArcaDiagnosticoService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/diagnostico")
    public ArcaDiagnosticoDto diagnostico(@RequestParam("puntoVenta") int puntoVenta) {
        return servicio.diagnosticar(puntoVenta);
    }
}
