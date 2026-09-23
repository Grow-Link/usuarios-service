package com.growlink.application;

import com.growlink.adapter.persistence.PerfilRepository;
import com.growlink.domain.Interes;
import com.growlink.domain.Nivel;
import com.growlink.domain.Perfil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
public class PerfilService {

    private final PerfilRepository perfilRepository;

    public PerfilService(PerfilRepository perfilRepository) {
        this.perfilRepository = perfilRepository;
    }

    // todo usuario tiene un perfil desde su primer login, aunque este vacio
    @Transactional
    public Perfil obtener(Long usuarioId) {
        return perfilRepository.findByUsuarioId(usuarioId)
                .orElseGet(() -> perfilRepository.save(new Perfil(usuarioId)));
    }

    @Transactional
    public Perfil actualizarMetas(Long usuarioId, String metas) {
        Perfil perfil = obtener(usuarioId);
        perfil.actualizarMetas(metas);
        return perfilRepository.save(perfil);
    }

    @Transactional
    public Perfil actualizarIntereses(Long usuarioId, Set<Interes> intereses) {
        Perfil perfil = obtener(usuarioId);
        perfil.actualizarIntereses(intereses);
        return perfilRepository.save(perfil);
    }

    @Transactional
    public Perfil actualizarNivel(Long usuarioId, Nivel nivel) {
        Perfil perfil = obtener(usuarioId);
        perfil.actualizarNivel(nivel);
        return perfilRepository.save(perfil);
    }
}
