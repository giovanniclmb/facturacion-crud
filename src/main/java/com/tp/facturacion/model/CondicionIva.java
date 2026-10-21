package com.tp.facturacion.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "condicion_iva")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CondicionIva extends AuditoriaApp {

    @Column(nullable = false)
    private int codigoAfip;

    @Column(nullable = false)
    private String denominacion;
}