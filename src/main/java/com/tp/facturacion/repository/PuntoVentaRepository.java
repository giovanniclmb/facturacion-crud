package com.tp.facturacion.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tp.facturacion.model.PuntoVenta;

public interface PuntoVentaRepository extends JpaRepository<PuntoVenta, Long> {

    List<PuntoVenta> findByNumeroInAndFechaBajaIsNullOrderByNumeroAscIdAsc(Collection<Integer> numeros);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from PuntoVenta p where p.id = :id")
    java.util.Optional<PuntoVenta> buscarBloqueado(@org.springframework.data.repository.query.Param("id") Long id);

}
