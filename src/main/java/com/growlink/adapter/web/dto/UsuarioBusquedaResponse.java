package com.growlink.adapter.web.dto;

import com.growlink.domain.Rol;
import com.growlink.domain.Usuario;

// lo minimo que se muestra al buscar a alguien para retarlo: nada de perfil ni metas
public record UsuarioBusquedaResponse(Long id, String nombre, Rol rol, String cargo, int triviasGanadas) {
    public static UsuarioBusquedaResponse from(Usuario u, int triviasGanadas) {
        return new UsuarioBusquedaResponse(u.getId(), u.getNombre(), u.getRol(), u.getCargo(), triviasGanadas);
    }
}
