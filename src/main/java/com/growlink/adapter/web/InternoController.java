package com.growlink.adapter.web;

import com.growlink.adapter.persistence.UsuarioRepository;
import com.growlink.application.PerfilService;
import com.growlink.application.UsuarioNoEncontradoException;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

// endpoints que solo usan otros servicios, no el frontend
// no llevan el token de un usuario, se protegen con una llave que comparten los servicios
@Hidden
@RestController
@RequestMapping("/api/interno")
public class InternoController {

    private final PerfilService perfilService;
    private final UsuarioRepository usuarioRepository;
    private final byte[] llaveInterna;

    public InternoController(PerfilService perfilService, UsuarioRepository usuarioRepository,
                             @Value("${growlink.internal-key}") String llaveInterna) {
        this.perfilService = perfilService;
        this.usuarioRepository = usuarioRepository;
        this.llaveInterna = llaveInterna.getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping("/trivias-ganadas/{usuarioId}")
    public ResponseEntity<Void> sumarTriviaGanada(@PathVariable Long usuarioId,
                                                   @RequestHeader(value = "X-Internal-Key", required = false) String llave) {
        // se compara en tiempo constante para no filtrar la llave por el tiempo de respuesta
        if (llave == null || !MessageDigest.isEqual(llaveInterna, llave.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(403).build();
        }
        if (!usuarioRepository.existsById(usuarioId)) {
            throw new UsuarioNoEncontradoException(usuarioId);
        }
        perfilService.sumarTriviaGanada(usuarioId);
        return ResponseEntity.noContent().build();
    }
}
