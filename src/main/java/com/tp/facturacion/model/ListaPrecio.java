package com.tp.facturacion.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "lista_precio")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ListaPrecio extends AuditoriaApp {

    @Column(nullable = false)
    private String codigo;

    @Column(nullable = false)
    private String denominacion;
}