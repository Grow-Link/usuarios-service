package com.growlink.application;

import com.growlink.adapter.persistence.PerfilRepository;
import com.growlink.adapter.persistence.UsuarioRepository;
import com.growlink.adapter.web.dto.UsuarioBusquedaResponse;
import com.growlink.domain.Perfil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UsuarioService {

    static final int MINIMO_LETRAS = 2;

    private final UsuarioRepository usuarioRepository;
    private final PerfilRepository perfilRepository;

    public UsuarioService(UsuarioRepository usuarioRepository, PerfilRepository perfilRepository) {
        this.usuarioRepository = usuarioRepository;
        this.perfilRepository = perfilRepository;
    }

    @Transactional(readOnly = true)
    public List<UsuarioBusquedaResponse> buscar(String texto, Long quienBusca) {
        // % y _ son comodines del LIKE: se quitan para que "%%" no devuelva a todo el mundo
        String q = texto == null ? "" : texto.replace("%", "").replace("_", "").trim();
        if (q.length() < MINIMO_LETRAS) {
            return List.of();
        }
        return usuarioRepository.buscar(q.toLowerCase(), quienBusca).stream()
                .map(u -> UsuarioBusquedaResponse.from(u,
                        perfilRepository.findByUsuarioId(u.getId()).map(Perfil::getTriviasGanadas).orElse(0)))
                .toList();
    }
}
