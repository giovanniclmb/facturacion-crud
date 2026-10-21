package com.tp.facturacion.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tp.facturacion.model.ListaPrecioArticulo;

public interface ListaPrecioArticuloRepository extends JpaRepository<ListaPrecioArticulo, Long> {

    // Precio vigente de un artículo: el último cargado que no esté dado de baja
    Optional<ListaPrecioArticulo> findFirstByArticuloIdAndFechaBajaIsNullOrderByIdDesc(Long articuloId);
}
