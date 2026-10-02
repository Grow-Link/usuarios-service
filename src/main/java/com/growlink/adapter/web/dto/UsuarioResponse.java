package com.growlink.adapter.web.dto;

import com.growlink.domain.Rol;
import com.growlink.domain.Usuario;

public record UsuarioResponse(Long id, String nombre, Rol rol, String cargo) {
    public static UsuarioResponse from(Usuario u) {
        return new UsuarioResponse(u.getId(), u.getNombre(), u.getRol(), u.getCargo());
    }
}
