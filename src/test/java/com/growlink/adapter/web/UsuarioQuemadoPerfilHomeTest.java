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

/**
 * Cubre HU-01 (usuario quemado), HU-04 (checkpoints de perfil) y HU-05
 * (home condicional) de punta a punta, contra H2 real - no mocks.
 */
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
        // 401, no 403 - ver la nota en SecurityConfig, ya nos paso este bug antes.
        mockMvc.perform(get("/api/perfil/me"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/perfil/me").header("Authorization", "Bearer token-inventado"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginQuemadoDaAccesoYElCaminoDePerfilSoloSeCompletaConLosTresCheckpoints() throws Exception {
        String token = loginComo(1L); // Ana, rol USUARIO (primer usuario del seed)
        String auth = "Bearer " + token;

        // Estado inicial: sin perfil completo -> Home debe pedir el camino de checkpoints.
        mockMvc.perform(get("/api/home/estado").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("SIN_PERFIL"));

        mockMvc.perform(get("/api/perfil/me").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completo").value(false));

        // Checkpoint 1 de 3.
        mockMvc.perform(put("/api/perfil/metas").header("Authorization", auth)
                        .contentType("application/json")
                        .content("{\"metas\": \"Quiero ser backend developer en 6 meses\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completo").value(false));

        // Checkpoint 2 de 3.
        mockMvc.perform(put("/api/perfil/intereses").header("Authorization", auth)
                        .contentType("application/json")
                        .content("{\"intereses\": [\"BACKEND\", \"BASES_DE_DATOS\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completo").value(false));

        // Checkpoint 3 de 3 - aqui SI se cierra el camino.
        mockMvc.perform(put("/api/perfil/nivel").header("Authorization", auth)
                        .contentType("application/json")
                        .content("{\"nivel\": \"PRINCIPIANTE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completo").value(true));

        // Home ya debe reflejar el perfil completo, en una peticion HTTP totalmente aparte.
        mockMvc.perform(get("/api/home/estado").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CON_PERFIL_SIN_ROADMAP"));
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
