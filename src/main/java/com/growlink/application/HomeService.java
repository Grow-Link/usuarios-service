package com.growlink.application;

import com.growlink.adapter.cursos.CursosServiceClient;
import com.growlink.adapter.persistence.UsuarioRepository;
import com.growlink.domain.EstadoHome;
import com.growlink.domain.Rol;
import com.growlink.domain.Seccion;
import com.growlink.domain.Usuario;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.Set;

// HU-05
@Service
public class HomeService {

    private final PerfilService perfilService;
    private final UsuarioRepository usuarioRepository;
    private final CursosServiceClient cursosServiceClient;

    public HomeService(PerfilService perfilService, UsuarioRepository usuarioRepository,
                        CursosServiceClient cursosServiceClient) {
        this.perfilService = perfilService;
        this.usuarioRepository = usuarioRepository;
        this.cursosServiceClient = cursosServiceClient;
    }

    public record ResumenHome(EstadoHome estado, Set<Seccion> secciones) {
    }

    public ResumenHome obtenerResumen(Long usuarioId, String bearerToken) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new UsuarioNoEncontradoException(usuarioId));
        boolean perfilCompleto = perfilService.obtener(usuarioId).estaCompleto();
        EstadoHome estado;
        if (!perfilCompleto) {
            estado = EstadoHome.SIN_PERFIL;
        } else if (cursosServiceClient.tieneRoadmap(usuarioId, bearerToken)) {
            estado = EstadoHome.CON_ROADMAP;
        } else {
            estado = EstadoHome.CON_PERFIL_SIN_ROADMAP;
        }
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
