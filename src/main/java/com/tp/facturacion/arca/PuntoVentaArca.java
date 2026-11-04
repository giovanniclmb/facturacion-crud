package com.tp.facturacion.arca;
import java.time.LocalDate;
public record PuntoVentaArca(int numero,String emisionTipo,boolean bloqueado,LocalDate fechaBaja) {}
