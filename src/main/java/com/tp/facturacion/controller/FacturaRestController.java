package com.tp.facturacion.controller;

import java.util.List;
import java.net.URI;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;

import com.tp.facturacion.dto.FacturaFiltroDto;
import com.tp.facturacion.dto.FacturaReporteDto;
import com.tp.facturacion.reporte.FacturaExcelExporter;
import com.tp.facturacion.reporte.FacturaPdfExporter;
import com.tp.facturacion.service.FacturaService;
import com.tp.facturacion.dto.FacturaRequestDto;

import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping("/api/facturas")
public class FacturaRestController {

    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final FacturaService facturaService;
    private final FacturaPdfExporter pdfExporter;
    private final FacturaExcelExporter excelExporter;

    public FacturaRestController(FacturaService facturaService,
                                 FacturaPdfExporter pdfExporter,
                                 FacturaExcelExporter excelExporter) {
        this.facturaService = facturaService;
        this.pdfExporter = pdfExporter;
        this.excelExporter = excelExporter;
    }

    // GET /api/facturas → JSON
    @GetMapping
    public List<FacturaReporteDto> buscar(@ModelAttribute FacturaFiltroDto filtro) {
        return buscarFacturas(filtro);
    }

    // GET /api/facturas/reporte/pdf → descarga PDF
    @GetMapping("/reporte/pdf")
    public ResponseEntity<byte[]> reportePdf(@ModelAttribute FacturaFiltroDto filtro) {
        byte[] pdf = pdfExporter.exportar(buscarFacturas(filtro));
        return descarga(pdf, MediaType.APPLICATION_PDF, "reporte-facturas.pdf");
    }

    // GET /api/facturas/reporte/excel → descarga .xlsx
    @GetMapping("/reporte/excel")
    public ResponseEntity<byte[]> reporteExcel(@ModelAttribute FacturaFiltroDto filtro) {
        byte[] excel = excelExporter.exportar(buscarFacturas(filtro));
        return descarga(excel, XLSX, "reporte-facturas.xlsx");
    }

    // GET /api/facturas/{id} → una factura
    @GetMapping("/{id:\\d+}")
    public FacturaReporteDto obtener(@PathVariable Long id) {
        return facturaService.obtenerReporte(id);
}

    // POST /api/facturas → crea una factura y responde 201 Created
    @PostMapping
    public ResponseEntity<FacturaReporteDto> crear(
        @Validated(FacturaRequestDto.Alta.class) @RequestBody FacturaRequestDto request) {

    FacturaReporteDto creada = facturaService.crear(request);
    return ResponseEntity
            .created(URI.create("/api/facturas/" + creada.getId()))
            .body(creada);
}

    // PUT /api/facturas/{id} → actualiza cabecera, ítems y/o estado. Responde 200 OK
    @PutMapping("/{id:\\d+}")
    public FacturaReporteDto actualizar(@PathVariable Long id,
                                    @Validated @RequestBody FacturaRequestDto request) {
        return facturaService.actualizar(id, request);
}

// DELETE /api/facturas/{id} → borrado físico (solo sin CAE). Responde 204 No Content
    @DeleteMapping("/{id:\\d+}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
    facturaService.eliminar(id);
        return ResponseEntity.noContent().build();
}

    private List<FacturaReporteDto> buscarFacturas(FacturaFiltroDto filtro) {
        return facturaService.buscarFacturasFiltradas(
                filtro.getFechaDesde(), filtro.getFechaHasta(),
                filtro.getEstado(), filtro.getMontoMinimo());
    }

    private ResponseEntity<byte[]> descarga(byte[] contenido, MediaType tipo, String nombreArchivo) {
        return ResponseEntity.ok()
                .contentType(tipo)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(nombreArchivo).build().toString())
                .body(contenido);
    }
}