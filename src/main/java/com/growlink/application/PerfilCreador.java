package com.growlink.application;

import com.growlink.adapter.persistence.PerfilRepository;
import com.growlink.domain.Perfil;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// Crea el perfil vacio en su PROPIA transaccion. Asi, si otra peticion lo creo justo antes y la base rechaza el
// duplicado, solo se deshace esta mini transaccion y la de quien llama sigue sana (si el choque ocurriera dentro
// de la misma transaccion, esta quedaria marcada para deshacerse aunque se atrape la excepcion).
@Component
public class PerfilCreador {

    private final PerfilRepository perfilRepository;

    public PerfilCreador(PerfilRepository perfilRepository) {
        this.perfilRepository = perfilRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void crear(Long usuarioId) {
        perfilRepository.saveAndFlush(new Perfil(usuarioId));
    }
}
