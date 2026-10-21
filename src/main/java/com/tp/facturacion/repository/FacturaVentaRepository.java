package com.tp.facturacion.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.tp.facturacion.model.FacturaVenta;

public interface FacturaVentaRepository extends JpaRepository<FacturaVenta, Long> {

    @Query("SELECT COALESCE(MAX(f.numero), 0) FROM FacturaVenta f WHERE f.puntoVenta.id = :puntoVentaId")
    Long ultimoNumeroPorPuntoVenta(@Param("puntoVentaId") Long puntoVentaId);
}