package com.growlink.adapter.persistence;

import com.growlink.domain.Perfil;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PerfilRepository extends JpaRepository<Perfil, Long> {

    Optional<Perfil> findByUsuarioId(Long usuarioId);

    // el mismo truco de siempre: la suma la hace la base en un solo UPDATE
    // si dos partidas terminan a la vez y el mismo usuario gana las dos, no se pierde ninguna
    // jardin: cada trivia ganada da tambien una gota de agua, en la MISMA operacion atomica
    @Modifying
    @Query("UPDATE Perfil p SET p.triviasGanadas = COALESCE(p.triviasGanadas, 0) + 1, "
            + "p.gotas = COALESCE(p.gotas, 0) + 1 WHERE p.usuarioId = :usuarioId")
    int sumarTriviaGanada(@Param("usuarioId") Long usuarioId);

    // atomico con condicion: si no hay gotas, 0 filas (no lee y luego escribe)
    @Modifying
    @Query("UPDATE Perfil p SET p.gotas = COALESCE(p.gotas, 0) - 1 "
            + "WHERE p.usuarioId = :usuarioId AND COALESCE(p.gotas, 0) > 0")
    int restarGota(@Param("usuarioId") Long usuarioId);
}
