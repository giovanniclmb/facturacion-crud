package com.tp.facturacion.arca.autorizacion;

import java.util.*;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import com.tp.facturacion.arca.ArcaProperties;
import com.tp.facturacion.dto.FacturaReporteDto;

@Component
public class ArcaReporteEnriquecedor {
    private final EntityManager em;
    private final ArcaProperties props;
    public ArcaReporteEnriquecedor(EntityManager em, ArcaProperties props) { this.em=em;this.props=props; }
    public List<FacturaReporteDto> completar(List<FacturaReporteDto> filas) {
        for(int inicio=0;inicio<filas.size();inicio+=500) {
            var lote=filas.subList(inicio,Math.min(inicio+500,filas.size()));
            var ids=lote.stream().map(FacturaReporteDto::getId).toList();
            var ultimos=new HashMap<Long,EstadoReporteArca>();
            em.createQuery("select new "+EstadoReporteArca.class.getName()+
                    "(i.factura.id,i.estado,i.numero,i.serie.puntoVenta,i.serie.tipo,i.vencimiento) "
                    +"from IntentoArca i where i.factura.id in :ids order by i.creado desc,i.id desc",EstadoReporteArca.class)
                    .setParameter("ids",ids).getResultList().forEach(i->ultimos.putIfAbsent(i.facturaId(),i));
            for(var fila:lote) {
                var ultimo=ultimos.get(fila.getId());
                if(ultimo!=null) {
                    fila.setEstadoFiscal(ultimo.estado());fila.setNumeroFiscal(ultimo.numero());
                    fila.setPuntoVentaFiscal(ultimo.puntoVenta());fila.setTipoComprobanteFiscal(ultimo.tipo());
                    fila.setCaeVencimiento(ultimo.vencimiento());
                }
                boolean conCae=fila.getCae()!=null&&!fila.getCae().isBlank();
                boolean activo=ultimo!=null&&IntentoArca.ESTADOS_ACTIVOS.contains(ultimo.estado());
                boolean anulada="ANULADA".equals(fila.getEstado());
                boolean libre=!conCae&&!activo;
                if(ultimo==null&&!conCae) fila.setMensajeArca(!props.isHabilitado() ? "ARCA está deshabilitada" :
                        (!props.getPuntosVentaExclusivos().contains(fila.getPuntoVentaNumero())
                                ? "Configure un punto de venta exclusivo para autorizar" : ""));
                fila.setPuedeAutorizar(props.isHabilitado()&&libre&&"PENDIENTE".equals(fila.getEstado())
                        && fila.getPuntoVentaNumero()!=null && props.getPuntosVentaExclusivos().contains(fila.getPuntoVentaNumero()));
                fila.setPuedeConsultar(ultimo!=null&&switch(ultimo.estado()) {
                    case PREPARANDO,ENVIANDO,INCIERTO -> true; default -> false;
                });
                fila.setPuedeModificar(libre&&!anulada);fila.setPuedeAnular(libre&&!anulada);
                fila.setPuedeEliminar(libre&&ultimo==null);
            }
        }
        return filas;
    }
}
