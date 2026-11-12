package com.growlink.adapter.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

// ATRIBUTO observabilidad (negocio): en modo legacy (el de por defecto) una sesion empieza con cada login que
// entrega un token, y solo con eso (ver RegistroDeSesiones). El modo oidc se prueba en IngresoConMicrosoftTest.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SesionesLegacyTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private MeterRegistry registro;

    @Test
    void cadaLoginQueEntregaUnTokenEsUnaSesion() throws Exception {
        double antes = sesiones("legacy");

        String token = login(1, 200);
        login(2, 200);

        assertThat(sesiones("legacy") - antes).isEqualTo(2);

        // usar el token no es empezar otra sesion
        mockMvc.perform(get("/api/usuarios/me").header("Authorization", "Bearer " + token));
        mockMvc.perform(get("/api/perfil/me").header("Authorization", "Bearer " + token));
        assertThat(sesiones("legacy") - antes).isEqualTo(2);
    }

    @Test
    void unLoginQueFallaNoEsUnaSesion() throws Exception {
        double antes = sesiones("legacy");

        login(999_999, 404);

        assertThat(sesiones("legacy")).isEqualTo(antes);
    }

    @Test
    void enModoLegacyNoSeCuentanSesionesPorActividad() throws Exception {
        double antes = sesiones("oidc");

        String token = login(3, 200);
        mockMvc.perform(get("/api/usuarios/me").header("Authorization", "Bearer " + token));

        // la regla de inactividad es solo del modo oidc: aqui la sesion ya la conto el login
        assertThat(sesiones("oidc")).isEqualTo(antes);
    }

    private String login(long usuarioId, int estadoEsperado) throws Exception {
        var respuesta = mockMvc.perform(post("/api/auth/login").contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("usuarioId", usuarioId))))
                .andReturn().getResponse();
        assertThat(respuesta.getStatus()).as("login de " + usuarioId).isEqualTo(estadoEsperado);
        return estadoEsperado == 200 ? objectMapper.readTree(respuesta.getContentAsString()).get("token").asText() : null;
    }

    private double sesiones(String modo) {
        var contador = registro.find("growlink.sesiones.iniciadas").tag("modo", modo).counter();
        return contador == null ? 0 : contador.count();
    }
}
