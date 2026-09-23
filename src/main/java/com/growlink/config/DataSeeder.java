package com.growlink.config;

import com.growlink.adapter.persistence.UsuarioRepository;
import com.growlink.domain.Rol;
import com.growlink.domain.Usuario;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

// crea un usuario fijo por cada rol, para poder probar sin registro real
@Component
public class DataSeeder implements CommandLineRunner {

    private final UsuarioRepository usuarioRepository;

    public DataSeeder(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public void run(String... args) {
        if (usuarioRepository.count() > 0) {
            return;
        }
        usuarioRepository.save(new Usuario("Ana (usuario)", Rol.USUARIO));
        usuarioRepository.save(new Usuario("Beto (publicador)", Rol.PUBLICADOR));
        usuarioRepository.save(new Usuario("Carla (admin)", Rol.ADMIN));
    }
}
