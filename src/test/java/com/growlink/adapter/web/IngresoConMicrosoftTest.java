package com.growlink.adapter.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.growlink.LlavesDePrueba;
import com.growlink.adapter.persistence.UsuarioRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

// El camino completo del modo auth.mode=oidc, con el servicio levantado: llega un token de Microsoft
// (firmado por el emisor de mentiras de LlavesDePrueba), se verifica, y el sub se convierte en un
// usuario local con su rol.
//
// Lo que se comprueba es el contrato que cursos-service y trivia-service van a usar: GET
// /api/usuarios/me traduce el sub del token a usuarioId numerico + rol.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        // base propia: este contexto no comparte los usuarios con el resto de las pruebas
        "spring.datasource.url=jdbc:h2:mem:growlinkoidc;MODE=PostgreSQL",
        "auth.mode=oidc",
        // ADMIN no se escoge: sale de esta lista. Con mayusculas a proposito, para comprobar que la
        // comparacion no las distingue
        "auth.admin-emails=Jefa@Growlink.CO, otra@growlink.co"
})
class IngresoConMicrosoftTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private MeterRegistry registro;

    // el emisor y la audiencia salen del servidor de pruebas, que escucha en un puerto libre: hay
    // que registrarlos antes de que arranque el contexto
    @DynamicPropertySource
    static void emisorDePruebas(DynamicPropertyRegistry propiedades) {
        propiedades.add("auth.oidc.issuer-uri", LlavesDePrueba::issuer);
        propiedades.add("auth.oidc.audience", () -> LlavesDePrueba.AUDIENCE);
    }

    // ---- primer ingreso y el que vuelve ----

    @Test
    void enElPrimerIngresoSeCreaElUsuarioLocalComoUsuarioSinRolElegido() throws Exception {
        String sub = unSub();

        JsonNode yo = me(LlavesDePrueba.tokenValido(sub, "ana@growlink.co", "Ana Torres"));

        assertThat(yo.get("usuarioId").isNumber()).as("el id tiene que ser numerico").isTrue();
        assertThat(yo.get("rol").asText()).isEqualTo("USUARIO");
        assertThat(yo.get("rolElegido").asBoolean()).isFalse();
        assertThat(yo.get("correo").asText()).isEqualTo("ana@growlink.co");
        assertThat(yo.get("nombre").asText()).isEqualTo("Ana Torres");
        // el usuario quedo guardado con el sub del token, que es la llave con la que se lo reconoce
        assertThat(usuarioRepository.findByProveedorSub(sub)).isPresent();
    }

    @Test
    void elIngresoRepetidoDevuelveElMismoUsuarioYNoDuplicaNada() throws Exception {
        String sub = unSub();
        String token = LlavesDePrueba.tokenValido(sub, "beto@growlink.co", "Beto Ramirez");
        long usuariosAntes = usuarioRepository.count();

        long primera = me(token).get("usuarioId").asLong();
        long segunda = me(token).get("usuarioId").asLong();
        // y con otro token del mismo sub (la persona volvio a entrar, el token es nuevo)
        long tercera = me(LlavesDePrueba.tokenValido(sub, "beto@growlink.co", "Beto Ramirez"))
                .get("usuarioId").asLong();

        assertThat(segunda).isEqualTo(primera);
        assertThat(tercera).isEqualTo(primera);
        assertThat(usuarioRepository.count()).as("solo se creo uno").isEqualTo(usuariosAntes + 1);
    }

    // hay cuentas sin correo publicado: el token es valido y la persona tiene que poder entrar
    @Test
    void unTokenSinCorreoTambienPuedeEntrar() throws Exception {
        JsonNode yo = me(LlavesDePrueba.tokenSinCorreo(unSub(), "Carla Jimenez"));

        assertThat(yo.get("usuarioId").isNumber()).isTrue();
        assertThat(yo.get("rol").asText()).isEqualTo("USUARIO");
        assertThat(yo.get("nombre").asText()).isEqualTo("Carla Jimenez");
        // con la configuracion de Jackson del proyecto, un correo nulo simplemente no sale
        assertThat(yo.has("correo")).isFalse();
    }

    // sin correo y sin nombre: el nombre es obligatorio en la base, asi que se arma uno
    @Test
    void unTokenSoloConSubTambienPuedeEntrar() throws Exception {
        JsonNode yo = me(LlavesDePrueba.tokenSoloConSub(unSub()));

        assertThat(yo.get("usuarioId").isNumber()).isTrue();
        assertThat(yo.get("nombre").asText()).isNotBlank();
    }

    // ---- escoger el rol ----

    @Test
    void laPersonaEscogePublicadorYPuedeVolverAUsuarioCuandoQuiera() throws Exception {
        String token = LlavesDePrueba.tokenValido(unSub(), "dani@growlink.co", "Daniela Perez");
        me(token); // primer ingreso

        JsonNode publicador = elegirRol(token, "PUBLICADOR", 200);
        assertThat(publicador.get("rol").asText()).isEqualTo("PUBLICADOR");
        assertThat(publicador.get("rolElegido").asBoolean()).isTrue();
        // y queda asi para las siguientes peticiones, no solo en la respuesta
        assertThat(me(token).get("rol").asText()).isEqualTo("PUBLICADOR");

        JsonNode usuario = elegirRol(token, "USUARIO", 200);
        assertThat(usuario.get("rol").asText()).isEqualTo("USUARIO");
        assertThat(usuario.get("rolElegido").asBoolean()).isTrue();
    }

    // ADMIN no se escoge: si se pudiera, cualquiera se lo daria
    @Test
    void pedirAdminSinEstarEnLaListaDa403YQuedaContado() throws Exception {
        String token = LlavesDePrueba.tokenValido(unSub(), "esteban@growlink.co", "Esteban Londono");
        me(token);
        double prohibidos0 = prohibidos();

        JsonNode error = elegirRol(token, "ADMIN", 403);

        assertThat(error.get("message").asText()).contains("ADMIN");
        // el intento queda contado, para ver desde las metricas si alguien esta insistiendo
        assertThat(prohibidos() - prohibidos0).isEqualTo(1);
        // y el rol no se movio
        assertThat(me(token).get("rol").asText()).isEqualTo("USUARIO");
    }

    @Test
    void unCorreoDeLaListaQuedaComoAdminDesdeElPrimerIngreso() throws Exception {
        // en el token viene en minusculas y en la lista con mayusculas: no se distinguen
        String token = LlavesDePrueba.tokenValido(unSub(), "jefa@growlink.co", "Jefa de Plataforma");

        JsonNode yo = me(token);

        assertThat(yo.get("rol").asText()).isEqualTo("ADMIN");
        // a un administrador no se le pregunta que quiere ser
        assertThat(yo.get("rolElegido").asBoolean()).isTrue();
    }

    @Test
    void unAdminNoPuedeBajarseElRolPorEsteEndpoint() throws Exception {
        String token = LlavesDePrueba.tokenValido(unSub(), "otra@growlink.co", "Otra Admin");
        assertThat(me(token).get("rol").asText()).isEqualTo("ADMIN");

        elegirRol(token, "USUARIO", 403);
        elegirRol(token, "PUBLICADOR", 403);

        assertThat(me(token).get("rol").asText()).isEqualTo("ADMIN");
    }

    // quien ya es ADMIN por la lista no esta intentando escalar nada al pedir ADMIN: no cambia nada
    @Test
    void siQuienPideAdminYaEstaEnLaListaNoEsUnIntentoDeEscalar() throws Exception {
        String token = LlavesDePrueba.tokenValido(unSub(), "jefa@growlink.co", "Jefa de Plataforma");
        me(token);

        JsonNode yo = elegirRol(token, "ADMIN", 200);

        assertThat(yo.get("rol").asText()).isEqualTo("ADMIN");
        assertThat(yo.get("rolElegido").asBoolean()).isTrue();
    }

    // ---- la puerta, con el servicio levantado ----

    @Test
    void sinTokenNoSeEntraYLaRespuestaTraeElIdentificadorDePeticion() throws Exception {
        MockHttpServletResponse respuesta = mockMvc.perform(get("/api/usuarios/me"))
                .andReturn().getResponse();

        assertThat(respuesta.getStatus()).isEqualTo(401);
        assertThat(respuesta.getHeader("X-Request-Id")).isNotBlank();
    }

    @Test
    void unTokenQueNoPasaLaVerificacionDa401YQuedaContado() throws Exception {
        double invalidos0 = tokensInvalidos();

        for (String token : new String[]{
                LlavesDePrueba.tokenVencido(unSub()),
                LlavesDePrueba.tokenDeOtroEmisor(unSub()),
                LlavesDePrueba.tokenParaOtraAudiencia(unSub()),
                LlavesDePrueba.tokenFirmadoPorUnImpostor(unSub()),
                LlavesDePrueba.tokenConAlgNone(unSub()),
                LlavesDePrueba.tokenConHs256FirmadoConLaLlavePublica(unSub())}) {
            MockHttpServletResponse respuesta = mockMvc.perform(get("/api/usuarios/me")
                    .header("Authorization", "Bearer " + token)).andReturn().getResponse();
            assertThat(respuesta.getStatus()).isEqualTo(401);
            // al cliente no se le dice que parte del token esta mal
            assertThat(respuesta.getContentAsString()).doesNotContain("emisor").doesNotContain("audiencia");
        }
        assertThat(tokensInvalidos() - invalidos0).isEqualTo(6);
    }

    // en modo oidc el token del login quemado ya no sirve: lo firma este servicio, no Microsoft
    @Test
    void enModoOidcElTokenDelLoginViejoNoSirve() throws Exception {
        String respuesta = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/auth/login").contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("usuarioId", 1))))
                .andReturn().getResponse().getContentAsString();
        String tokenViejo = objectMapper.readTree(respuesta).get("token").asText();

        assertThat(mockMvc.perform(get("/api/usuarios/me").header("Authorization", "Bearer " + tokenViejo))
                .andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    // la salud no puede depender de la autenticacion: la pregunta el balanceador, sin token
    @Test
    void laSaludSigueRespondiendoSinToken() throws Exception {
        assertThat(mockMvc.perform(get("/actuator/health")).andReturn().getResponse().getStatus())
                .isEqualTo(200);
    }

    // ---- sesiones iniciadas (ver RegistroDeSesiones; el paso del tiempo se prueba en RegistroDeSesionesTest) ----

    @Test
    void elPrimerIngresoEmpiezaUnaSesionYLoQueSigueEsLaMismaSesion() throws Exception {
        String sub = unSub();
        double sesiones0 = sesiones("oidc");

        me(LlavesDePrueba.tokenValido(sub, "fer@growlink.co", "Fernanda Gil"));
        assertThat(sesiones("oidc") - sesiones0).as("el primer ingreso es una sesion").isEqualTo(1);

        // lo que pasa despues: el frontend recarga y cursos-service o trivia-service preguntan por la identidad,
        // incluso con un token renovado. Es la misma persona usando la app: la misma sesion
        me(LlavesDePrueba.tokenValido(sub, "fer@growlink.co", "Fernanda Gil"));
        me(LlavesDePrueba.tokenValido(sub, "fer@growlink.co", "Fernanda Gil"));
        elegirRol(LlavesDePrueba.tokenValido(sub, "fer@growlink.co", "Fernanda Gil"), "PUBLICADOR", 200);
        assertThat(sesiones("oidc") - sesiones0).isEqualTo(1);
    }

    @Test
    void unTokenQueNoPasaNoEmpiezaNingunaSesion() throws Exception {
        double sesiones0 = sesiones("oidc");

        mockMvc.perform(get("/api/usuarios/me").header("Authorization", "Bearer "
                + LlavesDePrueba.tokenFirmadoPorUnImpostor(unSub())));

        assertThat(sesiones("oidc")).isEqualTo(sesiones0);
    }

    @Test
    void enModoOidcElLoginViejoNoCuentaComoSesion() throws Exception {
        double legacy0 = sesiones("legacy");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/auth/login").contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of("usuarioId", 1))));

        // ese token no abre nada en este modo (ver enModoOidcElTokenDelLoginViejoNoSirve)
        assertThat(sesiones("legacy")).isEqualTo(legacy0);
    }

    // ---- utilidades ----

    private static String unSub() {
        return UUID.randomUUID().toString();
    }

    private JsonNode me(String token) throws Exception {
        String cuerpo = mockMvc.perform(get("/api/usuarios/me").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(cuerpo);
    }

    private JsonNode elegirRol(String token, String rol, int estadoEsperado) throws Exception {
        MockHttpServletResponse respuesta = mockMvc.perform(put("/api/usuarios/me/rol")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"rol\":\"" + rol + "\"}"))
                .andReturn().getResponse();
        assertThat(respuesta.getStatus()).as("PUT /me/rol con " + rol).isEqualTo(estadoEsperado);
        return objectMapper.readTree(respuesta.getContentAsString(StandardCharsets.UTF_8));
    }

    private double tokensInvalidos() {
        return contador("growlink.seguridad.tokens-invalidos");
    }

    private double prohibidos() {
        return contador("growlink.seguridad.prohibidos");
    }

    private double contador(String nombre) {
        var contador = registro.find(nombre).counter();
        return contador == null ? 0 : contador.count();
    }

    private double sesiones(String modo) {
        var contador = registro.find("growlink.sesiones.iniciadas").tag("modo", modo).counter();
        return contador == null ? 0 : contador.count();
    }
}
