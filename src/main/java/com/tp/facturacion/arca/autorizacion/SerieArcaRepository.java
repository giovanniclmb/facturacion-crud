package com.tp.facturacion.arca.autorizacion;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface SerieArcaRepository extends JpaRepository<SerieArca,Long> {
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select s from SerieArca s where s.ambiente=:a and s.emisor=:e and s.puntoVenta=:p and s.tipo=:t")
 Optional<SerieArca> bloquear(@Param("a") String a,@Param("e") String e,@Param("p") int p,@Param("t") int t);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select s from SerieArca s where s.id=:id")
 Optional<SerieArca> bloquearId(@Param("id") long id);
}
