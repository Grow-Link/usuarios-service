package com.growlink.config;

import com.growlink.adapter.persistence.PerfilRepository;
import com.growlink.adapter.persistence.UsuarioRepository;
import com.growlink.domain.Interes;
import com.growlink.domain.Nivel;
import com.growlink.domain.Perfil;
import com.growlink.domain.Rol;
import com.growlink.domain.Usuario;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Set;

// crea un usuario fijo por cada rol, para poder probar sin registro real
@Component
public class DataSeeder implements CommandLineRunner {

    // Esteban es el usuario de demostracion: cursos-service le siembra un roadmap y cursos ya aprobados
    // (ver CursoCatalogoSeeder), por eso su perfil ya viene completo y con la misma meta
    static final long USUARIO_DEMO_ID = 5L;
    static final String USUARIO_DEMO_META = "Quiero ser desarrollador backend y trabajar con datos usando Python";

    private final UsuarioRepository usuarioRepository;
    private final PerfilRepository perfilRepository;

    public DataSeeder(UsuarioRepository usuarioRepository, PerfilRepository perfilRepository) {
        this.usuarioRepository = usuarioRepository;
        this.perfilRepository = perfilRepository;
    }

    @Override
    public void run(String... args) {
        if (usuarioRepository.count() == 0) {
            sembrarUsuarios();
        }
        completarPerfilDemo();
    }

    // solo si el perfil de Esteban esta incompleto: si alguien ya lo lleno a mano, no se pisa
    private void completarPerfilDemo() {
        if (!usuarioRepository.existsById(USUARIO_DEMO_ID)) {
            return;
        }
        Perfil perfil = perfilRepository.findByUsuarioId(USUARIO_DEMO_ID).orElseGet(() -> new Perfil(USUARIO_DEMO_ID));
        if (perfil.estaCompleto()) {
            return;
        }
        perfil.actualizarMetas(USUARIO_DEMO_META);
        perfil.actualizarIntereses(Set.of(Interes.INGENIERIA_SISTEMAS));
        perfil.actualizarNivel(Nivel.PRINCIPIANTE);
        perfilRepository.save(perfil);
    }

    private void sembrarUsuarios() {
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
