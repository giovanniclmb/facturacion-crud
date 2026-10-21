package com.tp.facturacion.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import lombok.*;

@Entity
@Table(name = "factura_venta")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = {"detalles"})
@ToString(callSuper = true, exclude = {"detalles"})
public class FacturaVenta extends AuditoriaApp {

    private Long numero;

    @Column(nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date fechaEmision;

    @ManyToOne
    @JoinColumn(name = "punto_venta_id", nullable = false)
    private PuntoVenta puntoVenta;

    private double importeCobrado;
    private double importeSaldo;

    @Column(nullable = false)
    private double importeTotal;

    private String cae;

    @Temporal(TemporalType.TIMESTAMP)
    private Date caeFechaVencimiento;

    private String resultadoAfip;
    private String motivoRechazo;

    @Column(nullable = false)
    private String estado;

    @Temporal(TemporalType.TIMESTAMP)
    private Date fechaAnulacion;

    private String observaciones;

    @OneToMany(mappedBy = "factura", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FacturaVentaDetalle> detalles = new ArrayList<>();

    @ManyToOne
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @ManyToOne
    @JoinColumn(name = "condicion_iva_id", nullable = false)
    private CondicionIva condicionIva;

    @ManyToOne
    @JoinColumn(name = "moneda_id", nullable = false)
    private TipoMoneda moneda;

    // Helper: agrega un detalle y sincroniza el lado dueño de la FK
    public void addDetalle(FacturaVentaDetalle detalle) {
        detalles.add(detalle);
        detalle.setFactura(this);
    }
}