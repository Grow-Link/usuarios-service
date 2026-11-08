package com.growlink.adapter.web.dto;

import com.growlink.domain.Rol;
import jakarta.validation.constraints.NotNull;

// El rol que la persona escoge en PUT /api/usuarios/me/rol. Se acepta USUARIO o PUBLICADOR; ADMIN
// llega hasta el servicio para poder responder 403 y dejar el intento en el log (ver IngresoService),
// en vez de rechazarlo aqui calladamente como si fuera un error de forma.
public record RolRequest(@NotNull Rol rol) {
}
