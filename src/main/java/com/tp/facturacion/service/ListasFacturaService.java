package com.tp.facturacion.service;

import com.tp.facturacion.dto.OpcionDto;
import com.tp.facturacion.model.ListaPrecio;
import com.tp.facturacion.model.ListaPrecioArticulo;
import com.tp.facturacion.repository.ListaPrecioArticuloRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ListasFacturaService {
    private final EntityManager em;
    private final ListaPrecioArticuloRepository precios;

    public ListasFacturaService(EntityManager em, ListaPrecioArticuloRepository precios) {
        this.em = em;
        this.precios = precios;
    }

    public List<OpcionDto> activas() {
        List<ListaPrecio> listas = em.createQuery("""
                select l from ListaPrecio l where l.fechaBaja is null
                order by case when (lower(trim(l.denominacion)) = 'general' or lower(trim(l.denominacion)) like 'lista general%') then 0 else 1 end,
                         l.denominacion, l.id
                """, ListaPrecio.class).getResultList();
        return listas.stream().map(l -> new OpcionDto(l.getId(), l.getDenominacion())).toList();
    }

    public ListaPrecio general() {
        return em.createQuery("""
                select l from ListaPrecio l where l.fechaBaja is null
                and (lower(trim(l.denominacion)) = 'general' or lower(trim(l.denominacion)) like 'lista general%')
                order by l.id
                """, ListaPrecio.class).setMaxResults(1).getResultList().stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No hay una lista General activa"));
    }

    public ListaPrecio activa(Long id) {
        if (id == null) return general();
        ListaPrecio lista = em.find(ListaPrecio.class, id);
        if (lista == null || lista.getFechaBaja() != null) {
            throw new IllegalArgumentException("La lista de precios seleccionada no está activa");
        }
        return lista;
    }

    public ListaPrecioArticulo precio(Long articulo, ListaPrecio lista) {
        return precioActivo(articulo, lista.getId()).or(() -> precioActivo(articulo, general().getId()))
                .orElseThrow(() -> new IllegalArgumentException(
                        "No hay precio vigente en la lista seleccionada ni en General para el artículo " + articulo));
    }

    public com.tp.facturacion.dto.ListaPrecioArticuloExplorerDto cotizar(Long articuloId, Long listaId) {
        var precio = precio(articuloId, activa(listaId));
        var articulo = precio.getArticulo();
        var lista = precio.getListaPrecio();
        return new com.tp.facturacion.dto.ListaPrecioArticuloExplorerDto(precio.getId(), articulo.getId(),
                articulo.getCodigo(), articulo.getDenominacion(), lista.getCodigo(), lista.getDenominacion(),
                precio.getPrecioVenta(), articulo.getIvaPorcentaje());
    }

    private java.util.Optional<ListaPrecioArticulo> precioActivo(Long articulo, Long lista) {
        return precios.findFirstByArticuloIdAndListaPrecioIdAndFechaBajaIsNullAndArticuloFechaBajaIsNullAndListaPrecioFechaBajaIsNullOrderByIdDesc(articulo, lista);
    }
}
