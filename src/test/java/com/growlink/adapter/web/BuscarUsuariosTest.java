package com.growlink.adapter.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// buscar personas (para retarlas a una trivia) y el perfil ya completo del usuario de demostracion
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BuscarUsuariosTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void sinTokenNoSePuedeBuscar() throws Exception {
        mockMvc.perform(get("/api/usuarios/buscar").param("q", "ana")).andExpect(status().isUnauthorized());
    }

    @Test
    void buscaPorNombreSinImportarMayusculas() throws Exception {
        JsonNode resultado = buscar("DANI", 1L);
        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).get("nombre").asText()).isEqualTo("Daniela Pérez");
        assertThat(resultado.get(0).get("triviasGanadas").asInt()).isGreaterThanOrEqualTo(0);
        // no se cuela nada del perfil
        assertThat(resultado.get(0).has("metas")).isFalse();
    }

    @Test
    void tambienBuscaPorCargoYNuncaDevuelveALaPersonaQueBusca() throws Exception {
        assertThat(buscar("banco", 1L)).hasSizeGreaterThanOrEqualTo(2);
        assertThat(buscar("Ana Torres", 1L)).isEmpty();
    }

    @Test
    void conMenosDeDosLetrasOSoloComodinesNoDevuelveNada() throws Exception {
        assertThat(buscar("a", 1L)).isEmpty();
        assertThat(buscar("", 1L)).isEmpty();
        assertThat(buscar("%%", 1L)).isEmpty();
        assertThat(buscar("__", 1L)).isEmpty();
    }

    @Test
    void elUsuarioDeDemostracionYaTieneElPerfilCompleto() throws Exception {
        String perfil = mockMvc.perform(get("/api/perfil/me").header("Authorization", "Bearer " + loginComo(5L)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode json = objectMapper.readTree(perfil);
        assertThat(json.get("metas").asText()).contains("backend");
        assertThat(json.get("nivel").asText()).isEqualTo("PRINCIPIANTE");
        assertThat(json.get("intereses").toString()).contains("INGENIERIA_SISTEMAS");
        assertThat(json.get("completo").asBoolean()).isTrue();
    }

    private JsonNode buscar(String q, long usuarioId) throws Exception {
        String cuerpo = mockMvc.perform(get("/api/usuarios/buscar").param("q", q)
                        .header("Authorization", "Bearer " + loginComo(usuarioId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(cuerpo);
    }

    private String loginComo(long usuarioId) throws Exception {
        String respuesta = mockMvc.perform(post("/api/auth/login").contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("usuarioId", usuarioId))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(respuesta).get("token").asText();
    }
}
