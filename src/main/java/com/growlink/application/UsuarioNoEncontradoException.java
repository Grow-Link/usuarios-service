package com.growlink.application;

public class UsuarioNoEncontradoException extends RuntimeException {
    public UsuarioNoEncontradoException(Long usuarioId) {
        super("No existe el usuario " + usuarioId);
    }
}
