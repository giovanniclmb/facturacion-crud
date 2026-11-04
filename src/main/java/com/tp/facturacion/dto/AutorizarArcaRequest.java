package com.tp.facturacion.dto;

/** Confirma el tipo de documento existente; no permite modificar los datos fiscales. */
public record AutorizarArcaRequest(boolean confirmarCuitReceptor) {}
