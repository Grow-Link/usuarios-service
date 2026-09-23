package com.growlink.adapter.web;

import com.growlink.adapter.web.dto.InteresesRequest;
import com.growlink.adapter.web.dto.MetasRequest;
import com.growlink.adapter.web.dto.NivelRequest;
import com.growlink.adapter.web.dto.PerfilResponse;
import com.growlink.application.PerfilService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** HU-04: un endpoint por checkpoint, para que cada paso del camino se guarde independiente. */
@RestController
@RequestMapping("/api/perfil")
public class PerfilController {

    private final PerfilService perfilService;

    public PerfilController(PerfilService perfilService) {
        this.perfilService = perfilService;
    }

    @GetMapping("/me")
    public PerfilResponse miPerfil(Authentication authentication) {
        return PerfilResponse.from(perfilService.obtener(usuarioId(authentication)));
    }

    @PutMapping("/metas")
    public PerfilResponse actualizarMetas(Authentication authentication, @Valid @RequestBody MetasRequest request) {
        return PerfilResponse.from(perfilService.actualizarMetas(usuarioId(authentication), request.metas()));
    }

    @PutMapping("/intereses")
    public PerfilResponse actualizarIntereses(Authentication authentication, @Valid @RequestBody InteresesRequest request) {
        return PerfilResponse.from(perfilService.actualizarIntereses(usuarioId(authentication), request.intereses()));
    }

    @PutMapping("/nivel")
    public PerfilResponse actualizarNivel(Authentication authentication, @Valid @RequestBody NivelRequest request) {
        return PerfilResponse.from(perfilService.actualizarNivel(usuarioId(authentication), request.nivel()));
    }

    private Long usuarioId(Authentication authentication) {
        return Long.valueOf(authentication.getName());
    }
}
