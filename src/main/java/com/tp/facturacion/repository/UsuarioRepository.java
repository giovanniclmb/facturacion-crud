package com.tp.facturacion.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tp.facturacion.model.Usuario;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
}