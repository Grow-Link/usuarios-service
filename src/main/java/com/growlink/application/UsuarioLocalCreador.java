package com.growlink.application;

import com.growlink.adapter.persistence.UsuarioRepository;
import com.growlink.domain.Usuario;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// Crea el usuario local del primer ingreso en su PROPIA transaccion, igual que PerfilCreador con el
// perfil. Asi, si otra peticion de la misma persona lo creo justo antes y la base rechaza el
// duplicado (proveedor_sub es unico), solo se deshace esta mini transaccion y la de quien llama
// sigue sana (si el choque ocurriera dentro de la misma transaccion, esta quedaria marcada para
// deshacerse aunque se atrape la excepcion).
//
// Pasa de verdad: al entrar, el frontend pide varias cosas a la vez (el perfil, el home, el
// jardin) y todas llegan con el mismo token de alguien que todavia no existe aqui.
@Component
public class UsuarioLocalCreador {

    private final UsuarioRepository usuarioRepository;

    public UsuarioLocalCreador(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Usuario crear(Usuario usuario) {
        return usuarioRepository.saveAndFlush(usuario);
    }
}
