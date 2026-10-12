package com.growlink.config;

import com.growlink.adapter.persistence.PerfilRepository;
import com.growlink.adapter.persistence.SemillaRepository;
import com.growlink.adapter.persistence.UsuarioRepository;
import com.growlink.domain.Interes;
import com.growlink.domain.Nivel;
import com.growlink.domain.Perfil;
import com.growlink.domain.Rol;
import com.growlink.domain.Semilla;
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

    // roadmapId inventados, solo para el jardin de demostracion (no vienen de cursos-service)
    static final long JARDIN_DEMO_ROADMAP_SEMILLA = 9001L;
    static final long JARDIN_DEMO_ROADMAP_PLANTA = 9002L;

    private final UsuarioRepository usuarioRepository;
    private final PerfilRepository perfilRepository;
    private final SemillaRepository semillaRepository;

    public DataSeeder(UsuarioRepository usuarioRepository, PerfilRepository perfilRepository,
                       SemillaRepository semillaRepository) {
        this.usuarioRepository = usuarioRepository;
        this.perfilRepository = perfilRepository;
        this.semillaRepository = semillaRepository;
    }

    @Override
    public void run(String... args) {
        if (usuarioRepository.count() == 0) {
            sembrarUsuarios();
        }
        completarPerfilDemo();
        sembrarJardinDemo();
    }

    // Esteban queda con 3 gotas, una semilla sin plantar y una planta con 2 riegos, para poder
    // presentar el jardin sin jugar nada. Solo se hace una vez (si ya existen, no se vuelve a tocar,
    // porque con ddl-auto: update la app se puede reiniciar muchas veces sobre la misma base).
    private void sembrarJardinDemo() {
        if (!usuarioRepository.existsById(USUARIO_DEMO_ID)) {
            return;
        }
        boolean yaTieneSemilla = semillaRepository.existsByUsuarioIdAndRoadmapId(USUARIO_DEMO_ID, JARDIN_DEMO_ROADMAP_SEMILLA);
        boolean yaTienePlanta = semillaRepository.existsByUsuarioIdAndRoadmapId(USUARIO_DEMO_ID, JARDIN_DEMO_ROADMAP_PLANTA);
        if (yaTieneSemilla && yaTienePlanta) {
            return;
        }
        if (!yaTieneSemilla) {
            semillaRepository.save(new Semilla(USUARIO_DEMO_ID, JARDIN_DEMO_ROADMAP_SEMILLA,
                    "INGENIERIA_SISTEMAS", "Fundamentos de programación"));
        }
        if (!yaTienePlanta) {
            Semilla planta = new Semilla(USUARIO_DEMO_ID, JARDIN_DEMO_ROADMAP_PLANTA,
                    "MATEMATICAS", "Álgebra lineal aplicada");
            planta.plantar(0);
            planta.regar();
            planta.regar();
            semillaRepository.save(planta);
        }
        Perfil perfil = perfilRepository.findByUsuarioId(USUARIO_DEMO_ID)
                .orElseGet(() -> perfilRepository.save(new Perfil(USUARIO_DEMO_ID)));
        perfil.sumarGotas(3);
        perfilRepository.save(perfil);
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
