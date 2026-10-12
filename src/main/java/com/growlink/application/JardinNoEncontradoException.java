package com.growlink.application;

public class JardinNoEncontradoException extends RuntimeException {
    public JardinNoEncontradoException(Long id) {
        super("No existe la semilla o planta " + id);
    }
}
