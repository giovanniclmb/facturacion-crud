package com.tp.facturacion.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tp.facturacion.model.Cliente;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {
}