package com.growlink.config;

import com.growlink.adapter.persistence.UsuarioRepository;
import com.growlink.domain.Rol;
import com.growlink.domain.Usuario;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/** HU-01 DoD: "usuarios seed cargados como fixture". Uno por rol, nada más. */
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
