package com.tp.facturacion.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "tipo_moneda")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class TipoMoneda extends AuditoriaApp {

    @Column(nullable = false)
    private String codigoAfip;

    @Column(nullable = false)
    private String denominacion;

    @Column(nullable = false)
    private String simbolo;
}