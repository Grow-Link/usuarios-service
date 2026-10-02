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
        // los primeros 3 (Ana, Beto, Carla) quedan con ids 1, 2 y 3: varias pruebas dependen de ese orden
        usuarioRepository.save(new Usuario("Ana Torres", Rol.USUARIO, "Analista Junior · Grupo Financiero Norte"));
        usuarioRepository.save(new Usuario("Beto Ramírez", Rol.PUBLICADOR, "Instructor · Growlink Academy"));
        usuarioRepository.save(new Usuario("Carla Jiménez", Rol.ADMIN, "Gerente de Plataforma · Growlink"));

        usuarioRepository.save(new Usuario("Daniela Pérez", Rol.USUARIO, "Practicante de Datos · Banco Región"));
        usuarioRepository.save(new Usuario("Esteban Londoño", Rol.USUARIO, "Desarrollador Junior · TechNova Solutions"));
        usuarioRepository.save(new Usuario("Fernanda Ruiz", Rol.USUARIO, "Asistente Administrativa · Industrias del Valle"));
        usuarioRepository.save(new Usuario("Gabriel Osorio", Rol.USUARIO, "Estudiante en Práctica · Universidad Central"));
        usuarioRepository.save(new Usuario("Helena Vargas", Rol.USUARIO, "Analista de Soporte · Conecta Seguros"));

        usuarioRepository.save(new Usuario("Iván Salazar", Rol.PUBLICADOR, "Fundador · CodeLab Bootcamp"));
        usuarioRepository.save(new Usuario("Juliana Moreno", Rol.PUBLICADOR, "Coordinadora de Datos · Banco Región"));
        usuarioRepository.save(new Usuario("Kevin Restrepo", Rol.PUBLICADOR, "Líder Técnico · Nimbus Cloud"));
        usuarioRepository.save(new Usuario("Laura Cifuentes", Rol.PUBLICADOR, "Docente · Instituto Politécnico del Sur"));
        usuarioRepository.save(new Usuario("Mateo Guzmán", Rol.PUBLICADOR, "Content Creator · DevTalks LATAM"));
    }
}
