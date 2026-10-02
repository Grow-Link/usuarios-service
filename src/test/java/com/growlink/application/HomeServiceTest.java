package com.growlink.application;

import com.growlink.adapter.cursos.CursosServiceClient;
import com.growlink.adapter.persistence.UsuarioRepository;
import com.growlink.domain.EstadoHome;
import com.growlink.domain.Perfil;
import com.growlink.domain.Rol;
import com.growlink.domain.Usuario;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HomeServiceTest {

    @Mock
    private PerfilService perfilService;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private CursosServiceClient cursosServiceClient;
    @Mock
    private Perfil perfil;

    private HomeService homeService() {
        return new HomeService(perfilService, usuarioRepository, cursosServiceClient);
    }

    @Test
    void sinPerfilCompletoNiSiquieraConsultaACursosService() {
        Usuario usuario = new Usuario("Ana Torres", Rol.USUARIO, null);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(perfilService.obtener(1L)).thenReturn(perfil);
        when(perfil.estaCompleto()).thenReturn(false);

        HomeService.ResumenHome resumen = homeService().obtenerResumen(1L, "Bearer token");

        assertThat(resumen.estado()).isEqualTo(EstadoHome.SIN_PERFIL);
        verifyNoInteractions(cursosServiceClient);
    }

    @Test
    void perfilCompletoSinRoadmapEnCursosService() {
        Usuario usuario = new Usuario("Ana Torres", Rol.USUARIO, null);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(perfilService.obtener(1L)).thenReturn(perfil);
        when(perfil.estaCompleto()).thenReturn(true);
        when(cursosServiceClient.tieneRoadmap(any(), anyString())).thenReturn(false);

        HomeService.ResumenHome resumen = homeService().obtenerResumen(1L, "Bearer token");

        assertThat(resumen.estado()).isEqualTo(EstadoHome.CON_PERFIL_SIN_ROADMAP);
    }

    @Test
    void perfilCompletoConRoadmapYaGeneradoEnCursosService() {
        Usuario usuario = new Usuario("Ana Torres", Rol.USUARIO, null);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(perfilService.obtener(1L)).thenReturn(perfil);
        when(perfil.estaCompleto()).thenReturn(true);
        when(cursosServiceClient.tieneRoadmap(1L, "Bearer token")).thenReturn(true);

        HomeService.ResumenHome resumen = homeService().obtenerResumen(1L, "Bearer token");

        assertThat(resumen.estado()).isEqualTo(EstadoHome.CON_ROADMAP);
    }
}
