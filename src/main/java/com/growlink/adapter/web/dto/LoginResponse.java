package com.growlink.adapter.web.dto;

import com.growlink.application.AuthService;

public record LoginResponse(String token, UsuarioResponse usuario) {
    public static LoginResponse from(AuthService.ResultadoLogin resultado) {
        return new LoginResponse(resultado.token(), UsuarioResponse.from(resultado.usuario()));
    }
}
