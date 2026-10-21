package com.tp.facturacion.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tp.facturacion.model.TipoMoneda;

public interface TipoMonedaRepository extends JpaRepository<TipoMoneda, Long> {
}