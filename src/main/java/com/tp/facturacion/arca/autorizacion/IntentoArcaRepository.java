package com.tp.facturacion.arca.autorizacion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface IntentoArcaRepository extends JpaRepository<IntentoArca,UUID> {
 Optional<IntentoArca> findFirstByFacturaIdOrderByCreadoDescIdDesc(Long facturaId);
 @org.springframework.data.jpa.repository.EntityGraph(attributePaths={"serie"})
 List<IntentoArca> findByFacturaIdInOrderByCreadoDescIdDesc(Collection<Long> ids);
 boolean existsByFacturaId(Long facturaId);
 boolean existsByFacturaIdAndEstadoIn(Long facturaId,Collection<EstadoIntentoArca> estados);
}
