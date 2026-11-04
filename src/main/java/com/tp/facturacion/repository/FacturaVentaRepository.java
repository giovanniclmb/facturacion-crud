package com.tp.facturacion.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.tp.facturacion.model.FacturaVenta;

public interface FacturaVentaRepository extends JpaRepository<FacturaVenta, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from FacturaVenta f where f.id = :id")
    java.util.Optional<FacturaVenta> buscarBloqueada(@Param("id") Long id);


    @Query("SELECT COALESCE(MAX(f.numero), 0) FROM FacturaVenta f WHERE f.puntoVenta.id = :puntoVentaId")
    Long ultimoNumeroPorPuntoVenta(@Param("puntoVentaId") Long puntoVentaId);
}