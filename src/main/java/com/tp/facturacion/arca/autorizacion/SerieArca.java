package com.tp.facturacion.arca.autorizacion;
import jakarta.persistence.*;
import lombok.Getter;
import java.util.UUID;
import static com.tp.facturacion.arca.autorizacion.ContratoFiscal.*;
@Entity @Table(name="arca_serie",uniqueConstraints=@UniqueConstraint(columnNames={"ambiente","emisor","punto_venta","tipo"})) @Getter
public class SerieArca {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false,columnDefinition="text") private String ambiente;
 @Column(nullable=false,columnDefinition="text") private String emisor;
 @Column(name="punto_venta",nullable=false) private int puntoVenta;
 @Column(nullable=false) private int tipo;
 @Column(name="intento_activo") UUID intentoActivo;
 public Serie serie(){return new Serie(ambiente,emisor,puntoVenta,tipo);}
}
