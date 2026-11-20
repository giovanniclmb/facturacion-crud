package com.tp.facturacion.service;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;

import com.tp.facturacion.arca.ArcaProperties;
import com.tp.facturacion.arca.autorizacion.SnapshotFiscalFactory;

import com.tp.facturacion.dto.ClienteExplorerDto;
import com.tp.facturacion.dto.ListaPrecioArticuloExplorerDto;
import com.tp.facturacion.dto.PaginaDto;
import com.tp.facturacion.dto.PaginacionDto;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CatalogoExplorerService {
    private final EntityManager em;

    private final ListasFacturaService listas;
    private final ArcaProperties arca;

    public CatalogoExplorerService(EntityManager em, ListasFacturaService listas, ArcaProperties arca) {
        this.em = em;
        this.listas = listas;
        this.arca = arca;
    }

    public PaginaDto<ClienteExplorerDto> clientes(String busqueda, PaginacionDto paginacion, boolean soloFacturaC) {
        Predicate<ClienteExplorerDto> elegible = soloFacturaC
                ? cliente -> SnapshotFiscalFactory.cuitValido(cliente.cuitCuil())
                    && !Objects.equals(cliente.cuitCuil(), arca.getCuit())
                : null;
        return buscar(busqueda, paginacion, ClienteExplorerDto.class,
                "c.id, c.denominacion, c.cuitCuil", "Cliente c", "c.id", "c.fechaBaja IS NULL",
                "LOWER(c.denominacion) LIKE :busqueda ESCAPE '!' OR LOWER(c.cuitCuil) LIKE :busqueda ESCAPE '!'",
                "c.denominacion, c.id", java.util.Map.of(), elegible);
    }

    public PaginaDto<ListaPrecioArticuloExplorerDto> listasPrecioArticulos(String busqueda,
                                                                         PaginacionDto paginacion) {
        return listasPrecioArticulos(busqueda, paginacion, null);
    }

    public PaginaDto<ListaPrecioArticuloExplorerDto> listasPrecioArticulos(
            String busqueda, PaginacionDto paginacion, Long listaPrecioId) {
        String vigencia = "lpa.fechaBaja IS NULL AND a.fechaBaja IS NULL AND lp.fechaBaja IS NULL";
        java.util.Map<String, Long> parametros = java.util.Map.of();
        if (listaPrecioId != null) {
            long seleccionada = listas.activa(listaPrecioId).getId();
            long general = listas.general().getId();
            parametros = java.util.Map.of("seleccionada", seleccionada, "general", general);
            vigencia += """
                     AND lpa.id = (SELECT MAX(elegido.id) FROM ListaPrecioArticulo elegido
                       WHERE elegido.articulo.id = a.id AND elegido.fechaBaja IS NULL
                       AND elegido.listaPrecio.id = COALESCE(
                         (SELECT MAX(preferido.listaPrecio.id) FROM ListaPrecioArticulo preferido
                          WHERE preferido.articulo.id = a.id AND preferido.fechaBaja IS NULL
                          AND preferido.listaPrecio.id = :seleccionada), :general))
                    """;
        }
        return buscar(busqueda, paginacion, ListaPrecioArticuloExplorerDto.class,
                "lpa.id, a.id, a.codigo, a.denominacion, lp.codigo, lp.denominacion, lpa.precioVenta",
                "ListaPrecioArticulo lpa JOIN lpa.articulo a JOIN lpa.listaPrecio lp", "lpa.id", vigencia,
                "LOWER(a.codigo) LIKE :busqueda ESCAPE '!' OR LOWER(a.denominacion) LIKE :busqueda ESCAPE '!' "
                        + "OR LOWER(lp.codigo) LIKE :busqueda ESCAPE '!' OR LOWER(lp.denominacion) LIKE :busqueda ESCAPE '!'",
                "a.denominacion, lp.denominacion, lpa.id", parametros, null);
    }

    private <T> PaginaDto<T> buscar(String busqueda, PaginacionDto pagina, Class<T> tipo,
                                   String campos, String desde, String id, String activos,
                                   String coincidencia, String orden, java.util.Map<String, Long> parametros,
                                   Predicate<T> elegible) {
        String termino = busqueda.trim().toLowerCase(Locale.ROOT);
        String fromWhere = " FROM " + desde + " WHERE " + activos
                + (termino.isEmpty() ? "" : " AND (" + coincidencia + ")");
        String patron = "%" + termino.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        long offset = (long) pagina.getPagina() * pagina.getTamanio();
        var consulta = em.createQuery("SELECT new " + tipo.getName() + "(" + campos + ")"
                + fromWhere + " ORDER BY " + orden, tipo);
        if (!termino.isEmpty()) consulta.setParameter("busqueda", patron);
        parametros.forEach(consulta::setParameter);

        if (elegible != null) {
            List<T> candidatos = consulta.getResultList().stream().filter(elegible).toList();
            int inicio = (int) Math.min(offset, candidatos.size());
            int fin = (int) Math.min(offset + pagina.getTamanio(), candidatos.size());
            return pagina(candidatos.subList(inicio, fin), pagina, candidatos.size());
        }

        var cuenta = em.createQuery("SELECT COUNT(" + id + ")" + fromWhere, Long.class);
        if (!termino.isEmpty()) {
            cuenta.setParameter("busqueda", patron);
        }
        parametros.forEach(cuenta::setParameter);
        long total = cuenta.getSingleResult();
        List<T> contenido = offset >= total ? List.of()
                : consulta.setFirstResult((int) offset).setMaxResults(pagina.getTamanio()).getResultList();
        return pagina(contenido, pagina, total);
    }

    private <T> PaginaDto<T> pagina(List<T> contenido, PaginacionDto pagina, long total) {
        return new PaginaDto<>(contenido, pagina.getPagina(), pagina.getTamanio(), total,
                (total + pagina.getTamanio() - 1) / pagina.getTamanio());
    }
}
