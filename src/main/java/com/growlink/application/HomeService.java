package com.growlink.application;

import com.growlink.adapter.persistence.UsuarioRepository;
import com.growlink.domain.EstadoHome;
import com.growlink.domain.Rol;
import com.growlink.domain.Seccion;
import com.growlink.domain.Usuario;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.Set;

// HU-05. CON_ROADMAP todavia no se puede dar porque no existe el roadmap
// eso llega cuando construyamos cursos-service
@Service
public class HomeService {

    private final PerfilService perfilService;
    private final UsuarioRepository usuarioRepository;

    public HomeService(PerfilService perfilService, UsuarioRepository usuarioRepository) {
        this.perfilService = perfilService;
        this.usuarioRepository = usuarioRepository;
    }

    public record ResumenHome(EstadoHome estado, Set<Seccion> secciones) {
    }

    public ResumenHome obtenerResumen(Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new UsuarioNoEncontradoException(usuarioId));
        boolean perfilCompleto = perfilService.obtener(usuarioId).estaCompleto();
        EstadoHome estado = perfilCompleto ? EstadoHome.CON_PERFIL_SIN_ROADMAP : EstadoHome.SIN_PERFIL;
        return new ResumenHome(estado, seccionesPara(usuario.getRol()));
    }

    // esto es HU-02 en la parte que si le toca a user-service, decidir que
    // secciones ve cada rol lo decide el backend, no el frontend
    // lo que falta de HU-02 (bloquear los endpoints de cursos y trivia) se
    // construye alla, no aqui
    private Set<Seccion> seccionesPara(Rol rol) {
        return switch (rol) {
            case USUARIO -> EnumSet.of(Seccion.TRIVIA, Seccion.PERFIL);
            case PUBLICADOR -> EnumSet.of(Seccion.TRIVIA, Seccion.PERFIL, Seccion.CURSOS, Seccion.PREGUNTAS_TRIVIA);
            case ADMIN -> EnumSet.of(Seccion.TRIVIA, Seccion.PERFIL, Seccion.CURSOS, Seccion.PREGUNTAS_TRIVIA,
                    Seccion.DAR_DE_BAJA_CURSOS, Seccion.DASHBOARD_METRICAS);
        };
    }
}
