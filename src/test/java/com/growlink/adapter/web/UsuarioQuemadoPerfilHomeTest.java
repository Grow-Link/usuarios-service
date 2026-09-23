package com.growlink.adapter.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// esta prueba cubre HU-01, HU-04 y HU-05 completas, contra una base real
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UsuarioQuemadoPerfilHomeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void listaLosTresUsuariosFixtureConSusRoles() throws Exception {
        mockMvc.perform(get("/api/auth/usuarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].rol").value("USUARIO"))
                .andExpect(jsonPath("$[1].rol").value("PUBLICADOR"))
                .andExpect(jsonPath("$[2].rol").value("ADMIN"));
    }

    @Test
    void meSinTokenEsRechazadoConCodigoDeAutenticacion() throws Exception {
        // tiene que dar 401, no 403, ya nos paso este bug antes
        mockMvc.perform(get("/api/perfil/me"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/perfil/me").header("Authorization", "Bearer token-inventado"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginQuemadoDaAccesoYElCaminoDePerfilSoloSeCompletaConLosTresCheckpoints() throws Exception {
        String token = loginComo(1L); // Ana, rol USUARIO (primer usuario del seed)
        String auth = "Bearer " + token;

        // al principio no hay perfil, entonces el home pide el camino de checkpoints
        mockMvc.perform(get("/api/home/estado").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("SIN_PERFIL"));

        mockMvc.perform(get("/api/perfil/me").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completo").value(false));

        // checkpoint 1 de 3
        mockMvc.perform(put("/api/perfil/metas").header("Authorization", auth)
                        .contentType("application/json")
                        .content("{\"metas\": \"Quiero ser backend developer en 6 meses\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completo").value(false));

        // checkpoint 2 de 3
        mockMvc.perform(put("/api/perfil/intereses").header("Authorization", auth)
                        .contentType("application/json")
                        .content("{\"intereses\": [\"BACKEND\", \"BASES_DE_DATOS\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completo").value(false));

        // checkpoint 3 de 3, aqui si se cierra el camino
        mockMvc.perform(put("/api/perfil/nivel").header("Authorization", auth)
                        .contentType("application/json")
                        .content("{\"nivel\": \"PRINCIPIANTE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completo").value(true));

        // pedimos el home en una peticion aparte, para confirmar que si quedo guardado
        mockMvc.perform(get("/api/home/estado").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CON_PERFIL_SIN_ROADMAP"));
    }

    @Test
    void cadaRolVeSusPropiasSecciones() throws Exception {
        // Ana es USUARIO, solo deberia ver trivia y perfil
        String tokenAna = loginComo(1L);
        mockMvc.perform(get("/api/home/estado").header("Authorization", "Bearer " + tokenAna))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secciones.length()").value(2))
                .andExpect(jsonPath("$.secciones", org.hamcrest.Matchers.containsInAnyOrder("TRIVIA", "PERFIL")));

        // Beto es PUBLICADOR, ademas ve cursos y preguntas de trivia
        String tokenBeto = loginComo(2L);
        mockMvc.perform(get("/api/home/estado").header("Authorization", "Bearer " + tokenBeto))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secciones.length()").value(4))
                .andExpect(jsonPath("$.secciones", org.hamcrest.Matchers.hasItems("CURSOS", "PREGUNTAS_TRIVIA")));

        // Carla es ADMIN, ve todo incluyendo dar de baja cursos y el dashboard
        String tokenCarla = loginComo(3L);
        mockMvc.perform(get("/api/home/estado").header("Authorization", "Bearer " + tokenCarla))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secciones.length()").value(6))
                .andExpect(jsonPath("$.secciones", org.hamcrest.Matchers.hasItems("DAR_DE_BAJA_CURSOS", "DASHBOARD_METRICAS")));
    }

    private String loginComo(Long usuarioId) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"usuarioId\": " + usuarioId + "}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(response, Map.class).get("token").toString();
    }
}
