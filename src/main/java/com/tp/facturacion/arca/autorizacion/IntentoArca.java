package com.tp.facturacion.arca.autorizacion;
import jakarta.persistence.*;
import lombok.Getter;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import com.tp.facturacion.model.FacturaVenta;
import com.tp.facturacion.model.Usuario;
import static com.tp.facturacion.arca.autorizacion.ContratoFiscal.*;
@Entity @Table(name="arca_intento") @Getter
public class IntentoArca {
 public static final Set<EstadoIntentoArca> ESTADOS_ACTIVOS=Set.of(EstadoIntentoArca.PREPARANDO,EstadoIntentoArca.ENVIANDO,EstadoIntentoArca.INCIERTO,EstadoIntentoArca.AUTORIZADO);
 @Id UUID id;
 @ManyToOne(optional=false) @JoinColumn(name="factura_id") FacturaVenta factura;
 @ManyToOne(optional=false) @JoinColumn(name="serie_id") SerieArca serie;
 @ManyToOne(optional=false) @JoinColumn(name="usuario_iniciador_id") Usuario usuarioIniciador;
 @Column(nullable=false) UUID propietario;
 @Enumerated(EnumType.STRING) @Column(nullable=false,columnDefinition="text") EstadoIntentoArca estado;
 Long numero;
 @Column(nullable=false) int concepto;
 @Column(name="doc_tipo",nullable=false) int docTipo;
 @Column(name="doc_numero",nullable=false) long docNumero;
 @Column(name="condicion_iva",nullable=false) int condicionIva;
 @Column(nullable=false) LocalDate fecha;
 @Column(nullable=false,columnDefinition="text") String moneda;
 @Column(nullable=false,precision=18,scale=6) BigDecimal cotizacion;
 @Column(nullable=false,precision=18,scale=2) BigDecimal total;
 @Column(nullable=false,columnDefinition="text") String hash;
 @Column(nullable=false) Instant creado;
 @Column(nullable=false) Instant actualizado;
 String cae;
 LocalDate vencimiento;
 @Column(nullable=false,columnDefinition="text") String errores="";
 @Column(nullable=false,columnDefinition="text") String observaciones="";
 @Version long version;
 public Snapshot getSnapshot(){return new Snapshot(concepto,docTipo,docNumero,condicionIva,fecha,moneda,cotizacion,total);}
 void snapshot(Snapshot s){concepto=s.concepto();docTipo=s.docTipo();docNumero=s.docNumero();condicionIva=s.condicionIva();fecha=s.fecha();moneda=s.moneda();cotizacion=s.cotizacion();total=s.total();}
 @Override public String toString(){return "IntentoArca["+id+","+estado+"]";}
}
