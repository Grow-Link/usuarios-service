package com.growlink.application;

import com.growlink.adapter.persistence.UsuarioRepository;
import com.growlink.config.AuthModeConfig;
import com.growlink.domain.Usuario;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

// ATRIBUTO: observabilidad (negocio). Cuantas sesiones empiezan, para el tablero de negocio: la metrica
// growlink.sesiones.iniciadas, con la etiqueta modo (legacy u oidc), y una linea de log por cada una.
//
// Que es "una sesion" depende del modo, porque en cada uno se ve distinto quien entra:
//
//   legacy  cada POST /api/auth/login que entrega un token. Ahi la persona escoge con quien entrar y recibe su
//           token: eso ES empezar una sesion, y no hay otra forma de hacerlo.
//   oidc    no hay login en este servicio: la persona inicia sesion en Microsoft y despues cada peticion trae su
//           token. Tampoco sirve contar GET /api/usuarios/me, porque cursos-service y trivia-service lo llaman
//           para resolver la identidad (cursos-service, una vez por minuto mientras la persona lo usa) y el
//           frontend lo llama en cada recarga. Se usa la regla de la analitica web: una sesion empieza con la
//           primera peticion de la persona despues de `inactividad` (30 min) sin ninguna. El primer ingreso
//           tambien cuenta.
//
// Para saber cuando fue la ultima actividad hace falta guardarla, y en la base, no en memoria: con varias replicas,
// cada una veria solo un pedazo de la actividad (ADR-0002). Para no convertir cada peticion en una escritura, la
// ultima actividad se corre como mucho cada `refresco` (5 min); por eso, en la practica, una sesion se da por
// terminada tras 25 a 30 minutos sin actividad. Las dos escrituras son UPDATE con condicion (ver
// UsuarioRepository): si dos peticiones de la misma persona llegan a la vez, a dos replicas o a la misma, la base
// deja pasar a una sola, y la sesion se cuenta una vez.
@Service
public class RegistroDeSesiones {

    public static final String METRICA = "growlink.sesiones.iniciadas";

    private static final Logger log = LoggerFactory.getLogger(RegistroDeSesiones.class);

    private final UsuarioRepository usuarioRepository;
    private final boolean modoLegacy;
    private final Duration inactividad;
    private final Duration refresco;
    private final Clock reloj;
    private final Counter porLogin;
    private final Counter porActividad;

    @Autowired
    public RegistroDeSesiones(UsuarioRepository usuarioRepository, MeterRegistry registro,
                              @Value("${auth.mode:legacy}") String modo,
                              @Value("${sesiones.inactividad-minutos:30}") long inactividadMinutos,
                              @Value("${sesiones.refresco-minutos:5}") long refrescoMinutos) {
        this(usuarioRepository, registro, modo, Duration.ofMinutes(inactividadMinutos),
                Duration.ofMinutes(refrescoMinutos), Clock.systemUTC());
    }

    // el reloj se inyecta para poder probar el paso del tiempo sin esperar de verdad
    RegistroDeSesiones(UsuarioRepository usuarioRepository, MeterRegistry registro, String modo,
                       Duration inactividad, Duration refresco, Clock reloj) {
        // Un refresco igual o mas largo que la inactividad haria que una sesion activa se diera por terminada
        // antes de refrescarla, y se contaria una sesion nueva en cada peticion: mejor no arrancar
        if (refresco.isNegative() || refresco.isZero() || refresco.compareTo(inactividad) >= 0) {
            throw new IllegalStateException("sesiones.refresco-minutos (" + refresco.toMinutes() + ") tiene que ser"
                    + " mayor que 0 y menor que sesiones.inactividad-minutos (" + inactividad.toMinutes() + ")");
        }
        this.usuarioRepository = usuarioRepository;
        this.modoLegacy = AuthModeConfig.MODO_LEGACY.equals(modo);
        this.inactividad = inactividad;
        this.refresco = refresco;
        this.reloj = reloj;
        // las dos se registran desde el arranque, para que el tablero vea un 0 y no un "sin datos"
        this.porLogin = contador(registro, AuthModeConfig.MODO_LEGACY);
        this.porActividad = contador(registro, AuthModeConfig.MODO_OIDC);
    }

    private static Counter contador(MeterRegistry registro, String modo) {
        return Counter.builder(METRICA)
                .description("sesiones que empiezan: un login en modo legacy; en modo oidc, la primera peticion"
                        + " tras el tiempo de inactividad")
                .tag("modo", modo)
                .register(registro);
    }

    /**
     * Modo legacy: alguien entro por POST /api/auth/login. En modo oidc ese token no abre nada (este servicio solo
     * acepta los de Microsoft), asi que ahi no se cuenta.
     */
    public void loginLegacy(Long usuarioId) {
        if (!modoLegacy) {
            return;
        }
        porLogin.increment();
        log.info("Sesion iniciada: usuario {} (login de demostracion)", usuarioId);
    }

    /**
     * Modo oidc: el usuario hizo una peticion con su token de Microsoft. Empieza una sesion si es su primera
     * actividad o si la anterior fue hace {@code inactividad} o mas; si no, como mucho se refresca la marca.
     *
     * @return true si con esta peticion empezo una sesion (y quedo contada)
     */
    @Transactional
    public boolean actividad(Usuario usuario) {
        Instant ahora = reloj.instant();
        Instant ultima = usuario.getUltimaActividad();

        // el camino de casi todas las peticiones: hubo actividad hace poco, no hay nada que escribir
        if (ultima != null && ultima.isAfter(ahora.minus(refresco))) {
            return false;
        }

        Instant inicioDeLaVentana = ahora.minus(inactividad);
        if (ultima == null || !ultima.isAfter(inicioDeLaVentana)) {
            // 0 filas = otra peticion de la misma persona empezo la sesion justo antes: ya esta contada
            if (usuarioRepository.iniciarSesion(usuario.getId(), ahora, inicioDeLaVentana) == 1) {
                porActividad.increment();
                log.info("Sesion iniciada: usuario {} ({})", usuario.getId(), ultima == null
                        ? "primera actividad con Microsoft"
                        : "sin actividad desde hace " + Duration.between(ultima, ahora).toMinutes() + " min");
                return true;
            }
            return false;
        }

        // sesion en curso: se corre la marca para que no se de por terminada mientras la persona sigue usando la app
        usuarioRepository.refrescarActividad(usuario.getId(), ahora, ahora.minus(refresco));
        return false;
    }
}
