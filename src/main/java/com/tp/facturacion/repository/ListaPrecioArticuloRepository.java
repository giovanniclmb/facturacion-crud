package com.tp.facturacion.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tp.facturacion.model.ListaPrecioArticulo;

public interface ListaPrecioArticuloRepository extends JpaRepository<ListaPrecioArticulo, Long> {

    // Último precio cargado con precio, artículo y lista activos.
    Optional<ListaPrecioArticulo> findFirstByArticuloIdAndFechaBajaIsNullAndArticuloFechaBajaIsNullAndListaPrecioFechaBajaIsNullOrderByIdDesc(Long articuloId);
}
