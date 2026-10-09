package com.growlink.adapter.web;

import com.growlink.adapter.web.dto.UsuarioBusquedaResponse;
import com.growlink.application.UsuarioService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/usuarios")
@Tag(name = "Usuarios", description = "Buscar a otras personas de la plataforma, por ejemplo para retarlas a una trivia")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    // busca por nombre o cargo (minimo 2 letras, maximo 10 resultados) y nunca devuelve a quien pregunta
    @GetMapping("/buscar")
    public List<UsuarioBusquedaResponse> buscar(Authentication authentication, @RequestParam(defaultValue = "") String q) {
        return usuarioService.buscar(q, Long.valueOf(authentication.getName()));
    }
}
