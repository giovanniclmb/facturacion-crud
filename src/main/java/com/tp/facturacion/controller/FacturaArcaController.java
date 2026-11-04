package com.tp.facturacion.controller;

import com.tp.facturacion.arca.autorizacion.AutorizacionArcaService;
import com.tp.facturacion.dto.AutorizarArcaRequest;
import com.tp.facturacion.dto.FacturaArcaDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Autorización explícita de homologación; GET nunca realiza operaciones remotas. */
@RestController
@RequestMapping("/api/facturas/{id:\\d+}/arca")
public class FacturaArcaController {
    private final AutorizacionArcaService servicio;
    public FacturaArcaController(AutorizacionArcaService servicio) { this.servicio=servicio; }
    @GetMapping public FacturaArcaDto estado(@PathVariable long id) { return servicio.estado(id); }
    @PostMapping("/autorizar")
    public ResponseEntity<FacturaArcaDto> autorizar(@PathVariable long id, @RequestBody AutorizarArcaRequest request) {
        return resultado(servicio.autorizar(id,request.confirmarCuitReceptor()));
    }
    @PostMapping("/consultar")
    public ResponseEntity<FacturaArcaDto> consultar(@PathVariable long id) { return resultado(servicio.consultar(id)); }
    private ResponseEntity<FacturaArcaDto> resultado(FacturaArcaDto dto) {
        boolean pendiente=dto.estadoFiscal()!=null && switch(dto.estadoFiscal()) {
            case PREPARANDO, ENVIANDO, INCIERTO -> true;
            default -> false;
        };
        return ResponseEntity.status(pendiente?202:200).body(dto);
    }
}
