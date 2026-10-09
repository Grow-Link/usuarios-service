package com.growlink.adapter.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

// Cuando alguien entra por primera vez, el front pide su perfil desde varios lugares a la vez. Si el perfil todavia
// no existe, todas esas peticiones intentan crearlo: ninguna debe fallar.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PerfilPrimeraVezConcurrenteTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void ochoPeticionesSimultaneasDelPerfilNuevoTerminanBien() throws Exception {
        String respuesta = mockMvc.perform(post("/api/auth/login").contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("usuarioId", 8)))).andReturn().getResponse().getContentAsString();
        String token = "Bearer " + objectMapper.readTree(respuesta).get("token").asText();

        int hilos = 8;
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch arrancar = new CountDownLatch(1);
        List<Future<Integer>> estados = new ArrayList<>();
        for (int i = 0; i < hilos; i++) {
            estados.add(pool.submit(() -> {
                arrancar.await();
                return mockMvc.perform(get("/api/perfil/me").header("Authorization", token)).andReturn().getResponse().getStatus();
            }));
        }
        arrancar.countDown();
        for (Future<Integer> f : estados) {
            assertThat(f.get(20, TimeUnit.SECONDS)).isEqualTo(200);
        }
        pool.shutdown();
    }
}
