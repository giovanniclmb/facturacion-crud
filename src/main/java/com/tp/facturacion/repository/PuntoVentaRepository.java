package com.tp.facturacion.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tp.facturacion.model.PuntoVenta;

public interface PuntoVentaRepository extends JpaRepository<PuntoVenta, Long> {
}
