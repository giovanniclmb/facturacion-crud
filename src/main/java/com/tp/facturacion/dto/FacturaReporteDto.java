package com.tp.facturacion.dto;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class FacturaReporteDto {

    private Long id;
    private Long numeroFactura;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd",
                timezone = "America/Argentina/Mendoza")
    private Date fechaEmision;

    private String clienteDenominacion;
    private String condicionIva;
    private String puntoVentaDescripcion;
    private double importeTotal;
    private long cantidadItems;
    private String estado;
    private String cae;
    private com.tp.facturacion.arca.autorizacion.EstadoIntentoArca estadoFiscal;
    private Long numeroFiscal;
    private Integer puntoVentaFiscal;
    private Integer puntoVentaNumero;
    private Integer tipoComprobanteFiscal;
    private java.time.LocalDate caeVencimiento;
    private String mensajeArca;
    private boolean puedeAutorizar;
    private boolean puedeConsultar;
    private boolean puedeModificar;
    private boolean puedeAnular;
    private boolean puedeEliminar;

    // Mantiene el contrato del constructor JPQL y de reportes previos.
    public FacturaReporteDto(Long id, Long numeroFactura, Date fechaEmision,
            String clienteDenominacion, String condicionIva, String puntoVentaDescripcion,
            double importeTotal, long cantidadItems, String estado, String cae) {
        this.id=id; this.numeroFactura=numeroFactura; this.fechaEmision=fechaEmision;
        this.clienteDenominacion=clienteDenominacion; this.condicionIva=condicionIva;
        this.puntoVentaDescripcion=puntoVentaDescripcion; this.importeTotal=importeTotal;
        this.cantidadItems=cantidadItems; this.estado=estado; this.cae=cae;
    }

    public FacturaReporteDto(Long id, Long numeroFactura, Date fechaEmision,
            String clienteDenominacion, String condicionIva, String puntoVentaDescripcion,
            double importeTotal, long cantidadItems, String estado, String cae,
            Long numeroFiscal, Integer puntoVentaFiscal, Integer tipoComprobanteFiscal, Date vencimiento, int puntoVentaNumero) {
        this(id,numeroFactura,fechaEmision,clienteDenominacion,condicionIva,puntoVentaDescripcion,
                importeTotal,cantidadItems,estado,cae);
        this.numeroFiscal=numeroFiscal;this.puntoVentaFiscal=puntoVentaFiscal;
        this.tipoComprobanteFiscal=tipoComprobanteFiscal;
        this.puntoVentaNumero=puntoVentaNumero;
        this.caeVencimiento=vencimiento==null?null:java.time.Instant.ofEpochMilli(vencimiento.getTime())
                .atZone(java.time.ZoneId.of("America/Argentina/Buenos_Aires")).toLocalDate();
    }

    public String numeroFiscalFormateado() {
        if (numeroFiscal == null || puntoVentaFiscal == null || tipoComprobanteFiscal == null)
            return "Sin número fiscal";
        return String.format("%05d-%08d (tipo %d)", puntoVentaFiscal, numeroFiscal, tipoComprobanteFiscal);
    }
    public String estadoFiscalTexto() {
        return estadoFiscal != null ? estadoFiscal.name() :
                (cae != null && !cae.isBlank() ? "AUTORIZADO (histórico)" : "Sin CAE");
    }
}
