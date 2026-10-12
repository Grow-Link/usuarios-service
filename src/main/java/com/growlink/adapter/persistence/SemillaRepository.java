package com.growlink.adapter.persistence;

import com.growlink.domain.Semilla;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SemillaRepository extends JpaRepository<Semilla, Long> {

    Optional<Semilla> findByIdAndUsuarioId(Long id, Long usuarioId);

    List<Semilla> findByUsuarioIdAndEspacioIsNullOrderByObtenidaEnAsc(Long usuarioId);

    List<Semilla> findByUsuarioIdAndEspacioIsNotNullOrderByEspacioAsc(Long usuarioId);

    boolean existsByUsuarioIdAndRoadmapId(Long usuarioId, Long roadmapId);

    // atomico: solo planta si seguia siendo semilla (si ya la habian plantado, 0 filas). El espacio ocupado
    // lo rechaza la restriccion unica (usuario_id, espacio), que vuelve DataIntegrityViolationException.
    // clearAutomatically: sin esto, quien llamo a findByIdAndUsuarioId antes (para validar dueño/existencia)
    // se queda con esa instancia vieja en el contexto de persistencia y la vuelve a leer tal cual, sin ver este UPDATE.
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Semilla s SET s.espacio = :espacio, s.plantadaEn = :ahora "
            + "WHERE s.id = :id AND s.usuarioId = :usuarioId AND s.espacio IS NULL")
    int plantar(@Param("id") Long id, @Param("usuarioId") Long usuarioId, @Param("espacio") Integer espacio,
                @Param("ahora") Instant ahora);

    // atomico con condicion (no lee y luego escribe): si ya estaba florecida (riegos = 3), 0 filas
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Semilla s SET s.riegos = s.riegos + 1 "
            + "WHERE s.id = :id AND s.usuarioId = :usuarioId AND s.espacio IS NOT NULL AND s.riegos < 3")
    int regar(@Param("id") Long id, @Param("usuarioId") Long usuarioId);

    // se corre justo despues de regar(): si ese riego la dejo en 3, le pone la fecha (solo una vez)
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Semilla s SET s.florecidaEn = :ahora WHERE s.id = :id AND s.riegos = 3 AND s.florecidaEn IS NULL")
    int marcarFlorecidaSiCorresponde(@Param("id") Long id, @Param("ahora") Instant ahora);
}
