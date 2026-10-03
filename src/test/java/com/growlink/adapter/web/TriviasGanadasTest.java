package com.growlink.adapter.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// HU-22: el contador de trivias ganadas, que le suma trivia-service cuando alguien gana
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TriviasGanadasTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Value("${growlink.internal-key}")
    private String llave;

    @Test
    void sinLlaveOConLlaveMalaNoSeDejaSumar() throws Exception {
        mockMvc.perform(post("/api/interno/trivias-ganadas/2"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/interno/trivias-ganadas/2").header("X-Internal-Key", "llave-inventada"))
                .andExpect(status().isForbidden());
    }

    @Test
    void usuarioQueNoExisteDa404() throws Exception {
        mockMvc.perform(post("/api/interno/trivias-ganadas/99999").header("X-Internal-Key", llave))
                .andExpect(status().isNotFound());
    }

    @Test
    void diezVictoriasAlMismoTiempoSumanExactamenteDiez() throws Exception {
        String auth = "Bearer " + loginComo(2L); // Beto
        int antes = triviasGanadas(auth); // tambien crea el perfil si todavia no existia

        int hilos = 10;
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch listos = new CountDownLatch(hilos);
        CountDownLatch arrancar = new CountDownLatch(1);
        List<Future<Integer>> resultados = new ArrayList<>();

        for (int i = 0; i < hilos; i++) {
            resultados.add(pool.submit(() -> {
                listos.countDown();
                arrancar.await();
                return mockMvc.perform(post("/api/interno/trivias-ganadas/2").header("X-Internal-Key", llave))
                        .andReturn().getResponse().getStatus();
            }));
        }
        listos.await();
        arrancar.countDown();
        for (Future<Integer> f : resultados) {
            assertThat(f.get(10, TimeUnit.SECONDS)).isEqualTo(204);
        }
        pool.shutdown();

        // si la suma se hiciera leyendo y escribiendo desde Java, aqui se perderian victorias
        assertThat(triviasGanadas(auth)).isEqualTo(antes + 10);
    }

    private int triviasGanadas(String auth) throws Exception {
        String json = mockMvc.perform(get("/api/perfil/me").header("Authorization", auth))
                .andReturn().getResponse().getContentAsString();
        return ((Number) objectMapper.readValue(json, Map.class).get("triviasGanadas")).intValue();
    }

    private String loginComo(Long usuarioId) throws Exception {
        String json = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"usuarioId\": " + usuarioId + "}"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(json, Map.class).get("token").toString();
    }
}
