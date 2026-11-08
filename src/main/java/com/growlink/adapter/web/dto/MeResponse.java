package com.growlink.adapter.web.dto;

import com.growlink.domain.Rol;
import com.growlink.domain.Usuario;

// Lo que devuelve GET /api/usuarios/me. Es el contrato con el que cursos-service y trivia-service
// resuelven "este sub de Microsoft es el usuario numero N y tiene este rol", asi que el usuarioId
// va numerico y con ese nombre: es el id que ellos ya usan en sus propias tablas.
//
// El correo puede no venir (hay tokens sin correo): con la configuracion de Jackson del proyecto
// (default-property-inclusion: non_null) el campo simplemente no sale en el JSON.
public record MeResponse(Long usuarioId, Rol rol, boolean rolElegido, String correo, String nombre) {

    public static MeResponse from(Usuario u) {
        return new MeResponse(u.getId(), u.getRol(), u.isRolElegido(), u.getCorreo(), u.getNombre());
    }
}
