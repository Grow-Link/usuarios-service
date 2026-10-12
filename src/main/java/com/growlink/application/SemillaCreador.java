package com.growlink.application;

import com.growlink.adapter.persistence.SemillaRepository;
import com.growlink.domain.Semilla;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// Crea la semilla en su PROPIA transaccion, igual que PerfilCreador con el perfil: si dos GET /api/jardin
// sincronizan el mismo roadmap completado al mismo tiempo, una choca con la restriccion unica
// (usuario_id, roadmap_id) y solo se deshace esta mini transaccion, no la del que llama.
@Component
public class SemillaCreador {

    private final SemillaRepository semillaRepository;

    public SemillaCreador(SemillaRepository semillaRepository) {
        this.semillaRepository = semillaRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void crear(Long usuarioId, Long roadmapId, String categoria, String titulo) {
        semillaRepository.saveAndFlush(new Semilla(usuarioId, roadmapId, categoria, titulo));
    }
}
