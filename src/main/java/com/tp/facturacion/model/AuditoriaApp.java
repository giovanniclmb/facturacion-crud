package com.tp.facturacion.model;

import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import lombok.*;

@MappedSuperclass
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public abstract class AuditoriaApp extends EntityId {

    @Column(nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    protected Date fechaAlta;

    @Temporal(TemporalType.TIMESTAMP)
    protected Date fechaBaja;

    @Column(nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    protected Date fechaModificacion;

    @ManyToOne
    @JoinColumn(name = "usuario_carga_id", nullable = false)
    protected Usuario usuarioCarga;

    @ManyToOne
    @JoinColumn(name = "usuario_baja_id")
    protected Usuario usuarioBaja;

    @ManyToOne
    @JoinColumn(name = "usuario_modificacion_id", nullable = false)
    protected Usuario usuarioModificacion;
}