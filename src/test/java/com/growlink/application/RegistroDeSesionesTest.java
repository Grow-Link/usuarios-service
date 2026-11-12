package com.growlink.application;

import com.growlink.adapter.persistence.UsuarioRepository;
import com.growlink.domain.Rol;
import com.growlink.domain.Usuario;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// ATRIBUTO observabilidad (negocio): cuando empieza una sesion en modo oidc (ver RegistroDeSesiones). La regla es la
// de la analitica web: una sesion empieza con la primera peticion despues de 30 minutos sin ninguna.
//
// Se usa la base de verdad (H2, con los mismos UPDATE condicionales que en Postgres) y un reloj de mentira, para
// probar el paso de los minutos sin esperarlos. Cada llamada va en su propia transaccion y relee al usuario, como
// pasa con cada peticion real.
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        // base propia: los usuarios de estas pruebas no se cruzan con las busquedas de las demas
        "spring.datasource.url=jdbc:h2:mem:growlinksesiones;MODE=PostgreSQL"
})
class RegistroDeSesionesTest {

    private static final Duration INACTIVIDAD = Duration.ofMinutes(30);
    private static final Duration REFRESCO = Duration.ofMinutes(5);

    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private PlatformTransactionManager transacciones;

    private RelojDePrueba reloj;
    private MeterRegistry registro;
    private RegistroDeSesiones sesiones;
    private Long usuarioId;

    @BeforeEach
    void preparar() {
        reloj = new RelojDePrueba(Instant.parse("2026-11-11T08:00:00Z"));
        registro = new SimpleMeterRegistry();
        sesiones = new RegistroDeSesiones(usuarioRepository, registro, "oidc", INACTIVIDAD, REFRESCO, reloj);
        usuarioId = usuarioRepository.save(Usuario.delProveedor(UUID.randomUUID().toString(), null,
                "Persona de Prueba", Rol.USUARIO, false)).getId();
    }

    @Test
    void elPrimerIngresoEmpiezaUnaSesion() {
        assertThat(peticion()).isTrue();

        assertThat(sesionesOidc()).isEqualTo(1);
        assertThat(ultimaActividad()).isEqualTo(reloj.instant());
    }

    @Test
    void lasPeticionesSeguidasSonLaMismaSesionYNoEscribenEnLaBase() {
        peticion();
        Instant inicio = reloj.instant();

        // cursos-service resuelve la identidad una vez por minuto mientras la persona lo usa
        for (int minuto = 1; minuto < 5; minuto++) {
            reloj.avanzar(Duration.ofMinutes(1));
            assertThat(peticion()).isFalse();
        }

        assertThat(sesionesOidc()).isEqualTo(1);
        // menos de 5 minutos despues no se toca la marca: la mayoria de las peticiones no escriben
        assertThat(ultimaActividad()).isEqualTo(inicio);
    }

    @Test
    void unaSesionLargaNoSeCortaMientrasLaPersonaSigaActiva() {
        peticion();

        // dos horas usando la app, con una peticion cada 4 minutos: nunca pasan 30 minutos sin actividad
        for (int i = 0; i < 30; i++) {
            reloj.avanzar(Duration.ofMinutes(4));
            assertThat(peticion()).as("peticion del minuto " + (4 * (i + 1))).isFalse();
        }

        assertThat(sesionesOidc()).isEqualTo(1);
    }

    @Test
    void despuesDeTreintaMinutosSinActividadEmpiezaOtra() {
        peticion();
        reloj.avanzar(Duration.ofMinutes(10));
        peticion(); // refresca la marca a los 10 minutos

        // 29 minutos despues de la ultima marca: sigue siendo la misma sesion
        reloj.avanzar(Duration.ofMinutes(29));
        assertThat(peticion()).isFalse();
        assertThat(sesionesOidc()).isEqualTo(1);

        // y la peticion de los 29 refresco la marca: 30 minutos mas tarde, sesion nueva
        reloj.avanzar(INACTIVIDAD);
        assertThat(peticion()).isTrue();
        assertThat(sesionesOidc()).isEqualTo(2);
    }

    @Test
    void justoAlCumplirseLaInactividadYaEsOtraSesion() {
        peticion();

        reloj.avanzar(INACTIVIDAD);

        assertThat(peticion()).isTrue();
        assertThat(sesionesOidc()).isEqualTo(2);
    }

    @Test
    void ochoPeticionesALaVezEnElPrimerIngresoCuentanUnaSolaSesion() throws Exception {
        // lo que pasa al entrar: el frontend pide el perfil, el home y el jardin a la vez, y cada uno puede caer en
        // una replica distinta. La base deja pasar un solo UPDATE condicional. (8 y no mas: cada hilo tiene abierta
        // una conexion mientras espera, y el pool de conexiones es de 10)
        int hilos = 8;
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch listos = new CountDownLatch(hilos);
        CountDownLatch arrancar = new CountDownLatch(1);
        List<Future<Boolean>> resultados = new ArrayList<>();
        for (int i = 0; i < hilos; i++) {
            resultados.add(pool.submit(() -> new TransactionTemplate(transacciones).execute(estado -> {
                // todas leen al usuario antes de que ninguna escriba: todas creen que empieza una sesion
                Usuario usuario = usuarioRepository.findById(usuarioId).orElseThrow();
                listos.countDown();
                try {
                    arrancar.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                return sesiones.actividad(usuario);
            })));
        }
        assertThat(listos.await(20, TimeUnit.SECONDS)).isTrue();
        arrancar.countDown();

        int empezadas = 0;
        for (Future<Boolean> resultado : resultados) {
            if (resultado.get(20, TimeUnit.SECONDS)) {
                empezadas++;
            }
        }
        pool.shutdown();

        assertThat(empezadas).as("solo una de las ocho empieza la sesion").isEqualTo(1);
        assertThat(sesionesOidc()).isEqualTo(1);
    }

    @Test
    void guardarElUsuarioPorOtraRazonNoLeDevuelveLaMarcaVieja() {
        peticion();
        Instant primera = reloj.instant();
        // una peticion lenta (escoger el rol, refrescar el correo) leyo al usuario con la marca de ese momento...
        Usuario leidoAntes = usuarioRepository.findById(usuarioId).orElseThrow();

        // ...mientras otra peticion corria la marca
        reloj.avanzar(Duration.ofMinutes(10));
        peticion();
        Instant segunda = reloj.instant();

        // al guardar, Hibernate escribe todas las columnas de la entidad. Si escribiera tambien esta, la regresaria a
        // la primera marca y la siguiente peticion veria una inactividad que no hubo (ver Usuario.ultimaActividad)
        leidoAntes.escogerRol(Rol.PUBLICADOR);
        usuarioRepository.save(leidoAntes);

        assertThat(ultimaActividad()).isEqualTo(segunda).isNotEqualTo(primera);
        assertThat(usuarioRepository.findById(usuarioId).orElseThrow().getRol()).isEqualTo(Rol.PUBLICADOR);
    }

    @Test
    void elContadorDelModoLegacyNoSeMueveConLaActividadNiConUnLoginEnModoOidc() {
        peticion();
        // en modo oidc el token de /api/auth/login no abre nada: no es una sesion
        sesiones.loginLegacy(usuarioId);

        assertThat(registro.get(RegistroDeSesiones.METRICA).tag("modo", "legacy").counter().count()).isZero();
        assertThat(sesionesOidc()).isEqualTo(1);
    }

    @Test
    void enModoLegacyCadaLoginEsUnaSesion() {
        RegistroDeSesiones legacy = new RegistroDeSesiones(usuarioRepository, registro, "legacy", INACTIVIDAD,
                REFRESCO, reloj);

        legacy.loginLegacy(usuarioId);
        legacy.loginLegacy(usuarioId);

        assertThat(registro.get(RegistroDeSesiones.METRICA).tag("modo", "legacy").counter().count()).isEqualTo(2);
    }

    @Test
    void unRefrescoQueNoEsMenorQueLaInactividadNoDejaArrancar() {
        // con refresco >= inactividad, una sesion activa se daria por terminada antes de refrescarla
        assertThatThrownBy(() -> new RegistroDeSesiones(usuarioRepository, registro, "oidc", INACTIVIDAD,
                INACTIVIDAD, reloj)).isInstanceOf(IllegalStateException.class).hasMessageContaining("refresco");
        assertThatThrownBy(() -> new RegistroDeSesiones(usuarioRepository, registro, "oidc", INACTIVIDAD,
                Duration.ZERO, reloj)).isInstanceOf(IllegalStateException.class);
    }

    // ---- utilidades ----

    /** Una peticion de la persona: su propia transaccion, con el usuario recien leido de la base. */
    private boolean peticion() {
        Boolean empezo = new TransactionTemplate(transacciones).execute(estado ->
                sesiones.actividad(usuarioRepository.findById(usuarioId).orElseThrow()));
        return Boolean.TRUE.equals(empezo);
    }

    private Instant ultimaActividad() {
        // la base puede guardar menos decimales que el reloj; los instantes de estas pruebas son minutos exactos
        Instant guardada = usuarioRepository.findById(usuarioId).orElseThrow().getUltimaActividad();
        return guardada == null ? null : guardada.truncatedTo(ChronoUnit.SECONDS);
    }

    private double sesionesOidc() {
        return registro.get(RegistroDeSesiones.METRICA).tag("modo", "oidc").counter().count();
    }

    private static final class RelojDePrueba extends Clock {

        private Instant ahora;

        private RelojDePrueba(Instant inicio) {
            this.ahora = inicio;
        }

        void avanzar(Duration cuanto) {
            ahora = ahora.plus(cuanto);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }
}
