package com.growlink.adapter.persistence;

import com.growlink.domain.Usuario;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    // Con esto se reconoce a quien vuelve a entrar con Microsoft: el sub del token es la llave
    // (columna unica, ver Usuario.proveedorSub), no el correo, que la persona puede cambiar.
    Optional<Usuario> findByProveedorSub(String proveedorSub);

    @Query("SELECT u FROM Usuario u WHERE u.id <> :excluir AND "
            + "(LOWER(u.nombre) LIKE CONCAT('%', :q, '%') OR LOWER(u.cargo) LIKE CONCAT('%', :q, '%')) "
            + "ORDER BY u.nombre")
    List<Usuario> buscar(@Param("q") String q, @Param("excluir") Long excluir, org.springframework.data.domain.Pageable limite);

    default List<Usuario> buscar(String q, Long excluir) {
        return buscar(q, excluir, PageRequest.of(0, 10));
    }

    // ---- actividad y sesiones (ver RegistroDeSesiones) ----
    //
    // Los dos son UPDATE con condicion, nunca leer-y-escribir: si llegan dos peticiones de la misma persona a la vez
    // (o a dos replicas), la base deja pasar a una y la otra encuentra la condicion ya falsa y no cambia nada. Asi
    // una sesion se cuenta una sola vez. Son SQL nativo porque la columna no se puede escribir desde la entidad (ver
    // Usuario.ultimaActividad). flushAutomatically: lo que la entidad tenga pendiente (un correo nuevo) se escribe
    // antes.

    /** Empieza una sesion si la ultima actividad es de antes de {@code limite} (o nunca hubo): 1 si la empezo esta. */
    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE usuario SET ultima_actividad = :ahora WHERE id = :id "
            + "AND (ultima_actividad IS NULL OR ultima_actividad <= :limite)", nativeQuery = true)
    int iniciarSesion(@Param("id") Long id, @Param("ahora") Instant ahora, @Param("limite") Instant limite);

    /** Corre la ultima actividad a {@code ahora} si es de antes de {@code limite}: no empieza ni cuenta nada. */
    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE usuario SET ultima_actividad = :ahora WHERE id = :id "
            + "AND ultima_actividad <= :limite", nativeQuery = true)
    int refrescarActividad(@Param("id") Long id, @Param("ahora") Instant ahora, @Param("limite") Instant limite);
}
