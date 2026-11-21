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
                    "(i.factura.id,i.estado,i.numero,i.serie.puntoVenta,i.serie.tipo,i.vencimiento,i.serie.ambiente,i.serie.emisor) "
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
                int tipo = ArcaProperties.tipoFiscal(fila.getTipoComprobante());
                boolean coincideEmisor = ultimo == null || props.coincideEmisorHomologacion(ultimo.ambiente(), ultimo.emisor());
                String impedimento = props.impedimentoAutorizacion(tipo, fila.getPuntoVentaNumero(), coincideEmisor);
                fila.setPuedeImprimir(true);
                if (tipo != 0 && !conCae) {
                    if (ultimo == null) {
                        fila.setMensajeArca(impedimento == null ? "" : impedimento);
                    } else if (impedimento != null) {
                        String detalle = fila.getMensajeArca();
                        fila.setMensajeArca(detalle == null || detalle.isBlank() ? impedimento : detalle + " · " + impedimento);
                    }
                }
                fila.setPuedeAutorizar(impedimento==null&&libre&&"PENDIENTE".equals(fila.getEstado()));
                fila.setPuedeConsultar(ultimo!=null&&coincideEmisor&&switch(ultimo.estado()) {
                    case PREPARANDO,ENVIANDO,INCIERTO -> true; default -> false;
                });
                fila.setPuedeModificar(libre&&"PENDIENTE".equals(fila.getEstado()));fila.setPuedeAnular(libre&&!anulada);
                fila.setPuedeEliminar(libre&&ultimo==null);
            }
        }
        return filas;
    }
}
