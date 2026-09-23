package com.growlink.application;

import com.growlink.domain.EstadoHome;
import org.springframework.stereotype.Service;

/**
 * HU-05. CON_ROADMAP queda sin alcanzar hasta que exista el modulo de
 * roadmap (HU-11) - de proposito no se inventa ese estado antes de tiempo.
 */
@Service
public class HomeService {

    private final PerfilService perfilService;

    public HomeService(PerfilService perfilService) {
        this.perfilService = perfilService;
    }

    public EstadoHome obtenerEstado(Long usuarioId) {
        boolean perfilCompleto = perfilService.obtener(usuarioId).estaCompleto();
        return perfilCompleto ? EstadoHome.CON_PERFIL_SIN_ROADMAP : EstadoHome.SIN_PERFIL;
    }
}
