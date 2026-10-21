package com.tp.facturacion.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tp.facturacion.dto.FacturaDetalleRequestDto;
import com.tp.facturacion.dto.FacturaReporteDto;
import com.tp.facturacion.dto.FacturaRequestDto;
import com.tp.facturacion.exception.RecursoNoEncontradoException;
import com.tp.facturacion.model.AuditoriaApp;
import com.tp.facturacion.model.FacturaVenta;
import com.tp.facturacion.model.FacturaVentaDetalle;
import com.tp.facturacion.model.ListaPrecioArticulo;
import com.tp.facturacion.model.PuntoVenta;
import com.tp.facturacion.model.Usuario;
import com.tp.facturacion.repository.ClienteRepository;
import com.tp.facturacion.repository.CondicionIvaRepository;
import com.tp.facturacion.repository.FacturaVentaRepository;
import com.tp.facturacion.repository.ListaPrecioArticuloRepository;
import com.tp.facturacion.repository.PuntoVentaRepository;
import com.tp.facturacion.repository.TipoMonedaRepository;
import com.tp.facturacion.repository.UsuarioRepository;
import com.tp.facturacion.exception.ReglaNegocioException;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

@Service
public class FacturaService {

    public static final String ESTADO_PENDIENTE = "PENDIENTE"; // creada, sin CAE
    public static final String ESTADO_EMITIDA = "EMITIDA";     // autorizada por ARCA
    public static final String ESTADO_ANULADA = "ANULADA";

    // Partes fijas de la consulta del reporte, compartidas por la búsqueda y por id
    private static final String SELECT_REPORTE =
        "SELECT new " + FacturaReporteDto.class.getName() + "(" +
        "    f.id, f.numero, f.fechaEmision, COALESCE(c.denominacion, 'Consumidor Final'), " +
        "    ci.denominacion, pv.descripcion, f.importeTotal, COUNT(d), f.estado, f.cae" +
        ") " +
        "FROM FacturaVenta f " +
        "LEFT JOIN f.cliente c " +
        "JOIN f.condicionIva ci " +
        "JOIN f.puntoVenta pv " +
        "LEFT JOIN f.detalles d ";

    private static final String GROUP_BY_REPORTE =
        "GROUP BY f.id, f.numero, f.fechaEmision, c.denominacion, ci.denominacion, " +
        "pv.descripcion, f.importeTotal, f.estado, f.cae ";

    @PersistenceContext
    private EntityManager em;

    private final FacturaVentaRepository facturaVentaRepository;
    private final PuntoVentaRepository puntoVentaRepository;
    private final ClienteRepository clienteRepository;
    private final CondicionIvaRepository condicionIvaRepository;
    private final TipoMonedaRepository tipoMonedaRepository;
    private final ListaPrecioArticuloRepository listaPrecioArticuloRepository;
    private final UsuarioRepository usuarioRepository;

    @Value("${facturacion.usuario-sistema-id}")
    private Long usuarioSistemaId;

    public FacturaService(FacturaVentaRepository facturaVentaRepository,
                          PuntoVentaRepository puntoVentaRepository,
                          ClienteRepository clienteRepository,
                          CondicionIvaRepository condicionIvaRepository,
                          TipoMonedaRepository tipoMonedaRepository,
                          ListaPrecioArticuloRepository listaPrecioArticuloRepository,
                          UsuarioRepository usuarioRepository) {
        this.facturaVentaRepository = facturaVentaRepository;
        this.puntoVentaRepository = puntoVentaRepository;
        this.clienteRepository = clienteRepository;
        this.condicionIvaRepository = condicionIvaRepository;
        this.tipoMonedaRepository = tipoMonedaRepository;
        this.listaPrecioArticuloRepository = listaPrecioArticuloRepository;
        this.usuarioRepository = usuarioRepository;
    }

    // ======================= LECTURA =======================

    @Transactional(readOnly = true)
    public List<FacturaReporteDto> buscarFacturasFiltradas(Date fechaDesde, Date fechaHasta,
                                                           String estado, Double montoMinimo) {

        StringBuilder jpql = new StringBuilder(SELECT_REPORTE).append("WHERE 1=1 ");
        Map<String, Object> params = new HashMap<>();

        if (fechaDesde != null) {
            jpql.append("AND f.fechaEmision >= :fechaDesde ");
            params.put("fechaDesde", fechaDesde);
        }
        if (fechaHasta != null) {
            // fechaEmision guarda fecha Y hora: para incluir todo el día "hasta",
            // comparamos con "menor que el día siguiente a las 00:00".
            jpql.append("AND f.fechaEmision < :fechaHastaExclusiva ");
            params.put("fechaHastaExclusiva", diaSiguiente(fechaHasta));
        }
        if (estado != null && !estado.trim().isEmpty()) {
            jpql.append("AND f.estado = :estado ");
            params.put("estado", estado.trim());
        }
        if (montoMinimo != null) {
            jpql.append("AND f.importeTotal >= :montoMinimo ");
            params.put("montoMinimo", montoMinimo);
        }

        jpql.append(GROUP_BY_REPORTE).append("ORDER BY f.fechaEmision, f.numero");

        TypedQuery<FacturaReporteDto> query =
                em.createQuery(jpql.toString(), FacturaReporteDto.class);
        params.forEach(query::setParameter);

        return query.getResultList();
    }

    @Transactional(readOnly = true)
    public FacturaReporteDto obtenerReporte(Long id) {
        List<FacturaReporteDto> resultado = em.createQuery(
                SELECT_REPORTE + "WHERE f.id = :id " + GROUP_BY_REPORTE, FacturaReporteDto.class)
                .setParameter("id", id)
                .getResultList();

        if (resultado.isEmpty()) {
            throw new RecursoNoEncontradoException("No existe la factura con id " + id);
        }
        return resultado.get(0);
    }

    // ======================= ALTA =======================

    @Transactional
    public FacturaReporteDto crear(FacturaRequestDto request) {
        Usuario usuario = usuarioSistema();
        Date ahora = new Date();

        // 1. Buscar las entidades asociadas (si alguna no existe → 404)
        PuntoVenta puntoVenta = buscarPorId(puntoVentaRepository, request.getPuntoVentaId(), "el punto de venta");

        FacturaVenta factura = new FacturaVenta();
        factura.setPuntoVenta(puntoVenta);
        factura.setCliente(buscarPorId(clienteRepository, request.getClienteId(), "el cliente"));
        factura.setCondicionIva(buscarPorId(condicionIvaRepository, request.getCondicionIvaId(), "la condición de IVA"));
        factura.setMoneda(buscarPorId(tipoMonedaRepository, request.getTipoMonedaId(), "la moneda"));

        // 2. Datos de cabecera
        factura.setNumero(facturaVentaRepository.ultimoNumeroPorPuntoVenta(puntoVenta.getId()) + 1);
        factura.setFechaEmision(request.getFechaEmision() != null ? request.getFechaEmision() : ahora);
        factura.setEstado(ESTADO_PENDIENTE);
        factura.setObservaciones(request.getObservaciones());

        // 3. Ítems (addDetalle mantiene sincronizados los dos lados de la relación)
        for (FacturaDetalleRequestDto detalleRequest : request.getDetalles()) {
            factura.addDetalle(crearDetalle(detalleRequest));
        }

        // 4. Importes calculados por el backend
        recalcularImportes(factura);
        factura.setImporteCobrado(0);
        factura.setImporteSaldo(factura.getImporteTotal());

        // 5. Auditoría
        factura.setFechaAlta(ahora);
        factura.setUsuarioCarga(usuario);
        registrarModificacion(factura, usuario, ahora);

        // 6. Un solo save: la cascada guarda también los detalles
        FacturaVenta guardada = facturaVentaRepository.save(factura);

        return obtenerReporte(guardada.getId());
    }

        // ======================= MODIFICACIÓN / ANULACIÓN =======================

    /**
     * Actualización parcial: solo se cambian los campos que vienen en el request.
     * Si vienen "detalles", reemplazan a todos los ítems actuales.
     */
    @Transactional
    public FacturaReporteDto actualizar(Long id, FacturaRequestDto request) {
        FacturaVenta factura = buscarPorId(facturaVentaRepository, id, "la factura");
        Usuario usuario = usuarioSistema();
        Date ahora = new Date();

        if (ESTADO_ANULADA.equals(factura.getEstado())) {
            throw new ReglaNegocioException("La factura " + id + " está anulada y no puede modificarse");
        }

        boolean cambiaContenido = request.getPuntoVentaId() != null
                || request.getClienteId() != null
                || request.getCondicionIvaId() != null
                || request.getTipoMonedaId() != null
                || request.getFechaEmision() != null
                || request.getDetalles() != null;

        if (cambiaContenido && tieneCae(factura)) {
            throw new ReglaNegocioException("La factura " + id + " ya fue autorizada por ARCA (CAE "
                    + factura.getCae() + "): no puede modificarse, solo anularse");
        }

        // Cabecera
        if (request.getPuntoVentaId() != null
                && !request.getPuntoVentaId().equals(factura.getPuntoVenta().getId())) {
            throw new ReglaNegocioException("El punto de venta de una factura no puede cambiarse");
        }
        if (request.getClienteId() != null) {
            factura.setCliente(buscarPorId(clienteRepository, request.getClienteId(), "el cliente"));
        }
        if (request.getCondicionIvaId() != null) {
            factura.setCondicionIva(buscarPorId(condicionIvaRepository, request.getCondicionIvaId(), "la condición de IVA"));
        }
        if (request.getTipoMonedaId() != null) {
            factura.setMoneda(buscarPorId(tipoMonedaRepository, request.getTipoMonedaId(), "la moneda"));
        }
        if (request.getFechaEmision() != null) {
            factura.setFechaEmision(request.getFechaEmision());
        }
        if (request.getObservaciones() != null) {
            factura.setObservaciones(request.getObservaciones());
        }

        // Ítems: se reemplazan todos
        if (request.getDetalles() != null) {
            if (request.getDetalles().isEmpty()) {
                throw new IllegalArgumentException("La factura debe tener al menos un ítem");
            }
            factura.getDetalles().clear(); // orphanRemoval borra los anteriores
            for (FacturaDetalleRequestDto detalleRequest : request.getDetalles()) {
                factura.addDetalle(crearDetalle(detalleRequest));
            }
            recalcularImportes(factura);
            factura.setImporteSaldo(redondear(factura.getImporteTotal() - factura.getImporteCobrado()));
        }

        // Estado
        if (request.getEstado() != null) {
            cambiarEstado(factura, request.getEstado().trim().toUpperCase(), ahora);
        }

        registrarModificacion(factura, usuario, ahora);

        // Sin save(): la entidad está "administrada" y Hibernate guarda los cambios solo
        return obtenerReporte(id);
    }

    // ======================= BAJA =======================

    @Transactional
    public void eliminar(Long id) {
        FacturaVenta factura = buscarPorId(facturaVentaRepository, id, "la factura");

        if (tieneCae(factura)) {
            throw new ReglaNegocioException("La factura " + id + " tiene CAE y no puede eliminarse: "
                    + "debe anularse (PUT con \"estado\": \"ANULADA\")");
        }

        facturaVentaRepository.delete(factura); // la cascada borra también los detalles
    }

    // ======================= AUXILIARES =======================

    private FacturaVentaDetalle crearDetalle(FacturaDetalleRequestDto request) {
        ListaPrecioArticulo precio = listaPrecioArticuloRepository
                .findFirstByArticuloIdAndFechaBajaIsNullOrderByIdDesc(request.getArticuloId())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe un precio vigente para el artículo con id " + request.getArticuloId()));

        FacturaVentaDetalle detalle = new FacturaVentaDetalle();
        detalle.setListaPrecioArticulo(precio);
        detalle.setDescripcion(request.getDescripcion() != null
                ? request.getDescripcion()
                : precio.getArticulo().getDenominacion());
        detalle.setCantidad(request.getCantidad());
        detalle.setPrecioUnitario(precio.getPrecioVenta());
        detalle.setPorcentajeBonificacion(request.getPorcentajeBonificacion() != null
                ? request.getPorcentajeBonificacion()
                : 0);
        return detalle;
    }

    /**
     * Factura C (emisor monotributista): no discrimina IVA.
     * subtotal = cantidad × precio − bonificación; neto = subtotal; IVA = 0.
     */
    private void recalcularImportes(FacturaVenta factura) {
        double total = 0;

        for (FacturaVentaDetalle detalle : factura.getDetalles()) {
            double bruto = detalle.getCantidad() * detalle.getPrecioUnitario();
            double bonificacion = bruto * detalle.getPorcentajeBonificacion() / 100;
            double subtotal = redondear(bruto - bonificacion);

            detalle.setImporteNeto(subtotal);
            detalle.setImporteIva(0);
            detalle.setImporteSubtotal(subtotal);

            total += subtotal;
        }

        factura.setImporteTotal(redondear(total));
    }

    private void registrarModificacion(AuditoriaApp entidad, Usuario usuario, Date fecha) {
        entidad.setFechaModificacion(fecha);
        entidad.setUsuarioModificacion(usuario);
    }

    private Usuario usuarioSistema() {
        return usuarioRepository.findById(usuarioSistemaId)
                .orElseThrow(() -> new IllegalStateException(
                        "No existe el usuario de sistema configurado (id " + usuarioSistemaId + ")"));
    }

    private <T> T buscarPorId(JpaRepository<T, Long> repositorio, Long id, String descripcion) {
        return repositorio.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe " + descripcion + " con id " + id));
    }

    private double redondear(double valor) {
        return BigDecimal.valueOf(valor).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private Date diaSiguiente(Date fecha) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(fecha);
        cal.add(Calendar.DAY_OF_MONTH, 1);
        return cal.getTime();
    }

        private void cambiarEstado(FacturaVenta factura, String nuevoEstado, Date ahora) {
        switch (nuevoEstado) {
            case ESTADO_ANULADA -> {
                factura.setEstado(ESTADO_ANULADA);
                factura.setFechaAnulacion(ahora);
            }
            case ESTADO_PENDIENTE, ESTADO_EMITIDA -> {
                if (!nuevoEstado.equals(factura.getEstado())) {
                    throw new ReglaNegocioException("El estado " + nuevoEstado + " no se asigna manualmente: "
                            + "una factura nace PENDIENTE y pasa a EMITIDA solo al autorizarse en ARCA");
                }
            }
            default -> throw new IllegalArgumentException("Estado desconocido: " + nuevoEstado
                    + ". Valores válidos: PENDIENTE, EMITIDA, ANULADA");
        }
    }

    private boolean tieneCae(FacturaVenta factura) {
        return factura.getCae() != null && !factura.getCae().isBlank();
    }
}