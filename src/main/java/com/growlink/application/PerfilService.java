package com.growlink.application;

import com.growlink.adapter.persistence.PerfilRepository;
import com.growlink.domain.Interes;
import com.growlink.domain.Nivel;
import com.growlink.domain.Perfil;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
public class PerfilService {

    private final PerfilRepository perfilRepository;
    private final PerfilCreador perfilCreador;

    public PerfilService(PerfilRepository perfilRepository, PerfilCreador perfilCreador) {
        this.perfilRepository = perfilRepository;
        this.perfilCreador = perfilCreador;
    }

    // todo usuario tiene un perfil desde su primer login, aunque este vacio.
    // Si dos peticiones piden el perfil por primera vez al mismo tiempo, las dos intentan crearlo y una choca con
    // la restriccion unica: ese choque NO es un error, solo significa que la otra ya lo creo, asi que se vuelve a leer.
    @Transactional
    public Perfil obtener(Long usuarioId) {
        return perfilRepository.findByUsuarioId(usuarioId).orElseGet(() -> {
            try {
                perfilCreador.crear(usuarioId);
            } catch (DataIntegrityViolationException e) {
                // otra peticion se adelanto: el perfil ya existe
            }
            return perfilRepository.findByUsuarioId(usuarioId).orElseThrow();
        });
    }

    // HU-22: la llama trivia-service cuando alguien gana una partida
    @Transactional
    public void sumarTriviaGanada(Long usuarioId) {
        obtener(usuarioId); // por si todavia no tenia perfil
        perfilRepository.sumarTriviaGanada(usuarioId);
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
