package com.growlink.application;

import com.growlink.adapter.cursos.CursosServiceClient;
import com.growlink.adapter.cursos.CursosServiceClient.RoadmapCompletado;
import com.growlink.adapter.persistence.PerfilRepository;
import com.growlink.adapter.persistence.SemillaRepository;
import com.growlink.domain.Semilla;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class JardinService {

    static final int ESPACIOS = 6;
    static final int RIEGOS_NECESARIOS = 3;

    private final SemillaRepository semillaRepository;
    private final SemillaCreador semillaCreador;
    private final PerfilService perfilService;
    private final PerfilRepository perfilRepository;
    private final CursosServiceClient cursosServiceClient;

    public JardinService(SemillaRepository semillaRepository, SemillaCreador semillaCreador,
                          PerfilService perfilService, PerfilRepository perfilRepository,
                          CursosServiceClient cursosServiceClient) {
        this.semillaRepository = semillaRepository;
        this.semillaCreador = semillaCreador;
        this.perfilService = perfilService;
        this.perfilRepository = perfilRepository;
        this.cursosServiceClient = cursosServiceClient;
    }

    public record JardinEstado(int gotas, int espacios, List<Semilla> semillas, List<Semilla> plantas) {
    }

    public record RegarResultado(int gotas, Semilla planta) {
    }

    // antes de responder, sincroniza semillas nuevas contra cursos-service (idempotente por roadmap)
    @Transactional
    public JardinEstado obtenerJardin(Long usuarioId, String bearerToken) {
        sincronizarSemillas(usuarioId, bearerToken);
        List<Semilla> semillas = semillaRepository.findByUsuarioIdAndEspacioIsNullOrderByObtenidaEnAsc(usuarioId);
        List<Semilla> plantas = semillaRepository.findByUsuarioIdAndEspacioIsNotNullOrderByEspacioAsc(usuarioId);
        int gotas = perfilService.obtener(usuarioId).getGotas();
        return new JardinEstado(gotas, ESPACIOS, semillas, plantas);
    }

    private void sincronizarSemillas(Long usuarioId, String bearerToken) {
        for (RoadmapCompletado completado : cursosServiceClient.roadmapsCompletados(usuarioId, bearerToken)) {
            if (semillaRepository.existsByUsuarioIdAndRoadmapId(usuarioId, completado.roadmapId())) {
                continue;
            }
            try {
                semillaCreador.crear(usuarioId, completado.roadmapId(), completado.categoria(), completado.titulo());
            } catch (DataIntegrityViolationException e) {
                // otra peticion ya la habia sincronizado
            }
        }
    }

    @Transactional
    public Semilla plantar(Long usuarioId, Long semillaId, int espacio) {
        Semilla semilla = semillaRepository.findByIdAndUsuarioId(semillaId, usuarioId)
                .orElseThrow(() -> new JardinNoEncontradoException(semillaId));
        if (semilla.getEspacio() != null) {
            throw new JardinConflictoException("La semilla " + semillaId + " ya esta plantada");
        }
        try {
            int filas = semillaRepository.plantar(semillaId, usuarioId, espacio, Instant.now());
            if (filas == 0) {
                throw new JardinConflictoException("La semilla " + semillaId + " ya esta plantada");
            }
        } catch (DataIntegrityViolationException e) {
            throw new JardinConflictoException("El espacio " + espacio + " ya esta ocupado");
        }
        return semillaRepository.findByIdAndUsuarioId(semillaId, usuarioId).orElseThrow();
    }

    // UPDATEs atomicos con condicion, no lee-y-escribe: si dos riegos llegan a la vez con una sola gota,
    // solo uno logra restar la gota; el otro se queda sin gotas y revierte todo (misma transaccion)
    @Transactional
    public RegarResultado regar(Long usuarioId, Long semillaId) {
        Semilla planta = semillaRepository.findByIdAndUsuarioId(semillaId, usuarioId)
                .orElseThrow(() -> new JardinNoEncontradoException(semillaId));
        if (planta.getEspacio() == null) {
            throw new JardinConflictoException("La planta " + semillaId + " todavia no esta plantada");
        }
        if (planta.getRiegos() >= RIEGOS_NECESARIOS) {
            throw new JardinConflictoException("La planta " + semillaId + " ya esta florecida");
        }
        if (perfilRepository.restarGota(usuarioId) == 0) {
            throw new JardinConflictoException("No tienes gotas de agua");
        }
        if (semillaRepository.regar(semillaId, usuarioId) == 0) {
            // se adelanto otro riego y la dejo florecida justo entre el chequeo de arriba y este UPDATE
            throw new JardinConflictoException("La planta " + semillaId + " ya esta florecida");
        }
        semillaRepository.marcarFlorecidaSiCorresponde(semillaId, Instant.now());

        int gotas = perfilService.obtener(usuarioId).getGotas();
        Semilla actualizada = semillaRepository.findByIdAndUsuarioId(semillaId, usuarioId).orElseThrow();
        return new RegarResultado(gotas, actualizada);
    }
}
