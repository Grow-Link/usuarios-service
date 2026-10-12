package com.growlink.adapter.web;

import com.growlink.adapter.web.dto.JardinResponse;
import com.growlink.adapter.web.dto.PlantaResponse;
import com.growlink.adapter.web.dto.PlantarRequest;
import com.growlink.adapter.web.dto.RegarResponse;
import com.growlink.application.JardinService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

// el jardin gamificado: una semilla por roadmap completado, una gota por trivia ganada
@RestController
@RequestMapping("/api/jardin")
@Tag(name = "Jardín", description = "Semillas, gotas y plantas del jardín personal de cada usuario")
public class JardinController {

    private final JardinService jardinService;

    public JardinController(JardinService jardinService) {
        this.jardinService = jardinService;
    }

    @Operation(summary = "El jardin del usuario (gotas, semillas sin plantar y plantas). "
            + "Antes de responder sincroniza semillas nuevas contra cursos-service.")
    @GetMapping
    public JardinResponse miJardin(Authentication authentication,
                                    @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return JardinResponse.from(jardinService.obtenerJardin(usuarioId(authentication), authorization));
    }

    @Operation(summary = "Planta una semilla propia en un espacio libre (0 a 5)")
    @PostMapping("/plantas/{id}/plantar")
    public PlantaResponse plantar(Authentication authentication, @PathVariable Long id,
                                   @Valid @RequestBody PlantarRequest request) {
        return PlantaResponse.from(jardinService.plantar(usuarioId(authentication), id, request.espacio()));
    }

    @Operation(summary = "Riega una planta propia: gasta 1 gota y suma 1 riego (maximo 3)")
    @PostMapping("/plantas/{id}/regar")
    public RegarResponse regar(Authentication authentication, @PathVariable Long id) {
        return RegarResponse.from(jardinService.regar(usuarioId(authentication), id));
    }

    private Long usuarioId(Authentication authentication) {
        return Long.valueOf(authentication.getName());
    }
}
