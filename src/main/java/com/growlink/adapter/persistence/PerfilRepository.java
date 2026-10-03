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
    @Modifying
    @Query("UPDATE Perfil p SET p.triviasGanadas = COALESCE(p.triviasGanadas, 0) + 1 WHERE p.usuarioId = :usuarioId")
    int sumarTriviaGanada(@Param("usuarioId") Long usuarioId);
}
