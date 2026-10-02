package com.growlink.adapter.web;

import com.growlink.adapter.web.dto.LoginRequest;
import com.growlink.adapter.web.dto.LoginResponse;
import com.growlink.adapter.web.dto.UsuarioResponse;
import com.growlink.application.AuthService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticación", description = "Login quemado y listado de usuarios disponibles")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // con esta lista se pinta la pantalla de elegir usuario, no hay registro real
    @GetMapping("/usuarios")
    public List<UsuarioResponse> usuariosDisponibles() {
        return authService.listarUsuariosDisponibles().stream().map(UsuarioResponse::from).toList();
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return LoginResponse.from(authService.login(request.usuarioId()));
    }
}
