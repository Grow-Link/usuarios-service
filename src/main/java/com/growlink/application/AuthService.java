package com.growlink.application;

import com.growlink.adapter.persistence.UsuarioRepository;
import com.growlink.adapter.security.TokenService;
import com.growlink.domain.Usuario;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final TokenService tokenService;

    public AuthService(UsuarioRepository usuarioRepository, TokenService tokenService) {
        this.usuarioRepository = usuarioRepository;
        this.tokenService = tokenService;
    }

    public List<Usuario> listarUsuariosDisponibles() {
        return usuarioRepository.findAll();
    }

    public record ResultadoLogin(String token, Usuario usuario) {
    }

    public ResultadoLogin login(Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new UsuarioNoEncontradoException(usuarioId));
        String token = tokenService.emitir(usuario.getId(), usuario.getRol());
        return new ResultadoLogin(token, usuario);
    }
}
