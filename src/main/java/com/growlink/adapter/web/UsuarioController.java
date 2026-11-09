package com.growlink.adapter.web;

import com.growlink.adapter.web.dto.MeResponse;
import com.growlink.adapter.web.dto.RolRequest;
import com.growlink.adapter.web.dto.UsuarioBusquedaResponse;
import com.growlink.application.IngresoService;
import com.growlink.application.UsuarioService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/usuarios")
@Tag(name = "Usuarios", description = "Quien soy, que rol tengo, y buscar a otras personas de la plataforma")
public class UsuarioController {

    private final UsuarioService usuarioService;
    private final IngresoService ingresoService;

    public UsuarioController(UsuarioService usuarioService, IngresoService ingresoService) {
        this.usuarioService = usuarioService;
        this.ingresoService = ingresoService;
    }

    // Quien es la persona del token. Es el endpoint con el que cursos-service y trivia-service
    // traducen el sub de Microsoft a usuarioId + rol, asi que tiene que servir con el token tal
    // como lo manda el frontend, sin llaves internas.
    //
    // En modo oidc el usuario local ya quedo creado al validar el token (OidcAuthenticationFilter),
    // asi que esto nunca es el que crea nada: solo lee.
    @GetMapping("/me")
    public MeResponse me(Authentication authentication) {
        return MeResponse.from(ingresoService.obtener(usuarioId(authentication)));
    }

    // La persona escoge entre USUARIO y PUBLICADOR, y puede cambiar cuando quiera. ADMIN no se
    // escoge: sale de la lista de correos AUTH_ADMIN_EMAILS, y pedirlo sin estar en ella da 403.
    @PutMapping("/me/rol")
    public MeResponse elegirRol(Authentication authentication, @Valid @RequestBody RolRequest request) {
        return MeResponse.from(ingresoService.elegirRol(usuarioId(authentication), request.rol()));
    }

    // busca por nombre o cargo (minimo 2 letras, maximo 10 resultados) y nunca devuelve a quien pregunta
    @GetMapping("/buscar")
    public List<UsuarioBusquedaResponse> buscar(Authentication authentication, @RequestParam(defaultValue = "") String q) {
        return usuarioService.buscar(q, usuarioId(authentication));
    }

    private Long usuarioId(Authentication authentication) {
        return Long.valueOf(authentication.getName());
    }
}
