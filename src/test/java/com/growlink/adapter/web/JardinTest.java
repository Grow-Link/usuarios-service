package com.growlink.adapter.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.growlink.adapter.cursos.CursosServiceClient;
import com.growlink.adapter.cursos.CursosServiceClient.RoadmapCompletado;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// el jardin: sincronizar semillas desde cursos-service, plantar y regar
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JardinTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Value("${growlink.internal-key}")
    private String llaveInterna;

    // cursos-service todavia esta construyendo /api/roadmap/completados en paralelo: lo mockeamos
    @MockBean
    private CursosServiceClient cursosServiceClient;

    @Test
    void sinTokenNoHayJardin() throws Exception {
        mockMvc.perform(get("/api/jardin")).andExpect(status().isUnauthorized());
    }

    @Test
    void sincronizaSemillasNuevasYEsIdempotenteAunqueSeLlameDosVeces() throws Exception {
        long usuarioId = 6L;
        when(cursosServiceClient.roadmapsCompletados(eq(usuarioId), anyString()))
                .thenReturn(List.of(new RoadmapCompletado(501L, "INGENIERIA_SISTEMAS", "Roadmap de sistemas")));
        String auth = "Bearer " + loginComo(usuarioId);

        JsonNode primeraVez = jardin(auth);
        assertThat(primeraVez.get("gotas").asInt()).isZero();
        assertThat(primeraVez.get("espacios").asInt()).isEqualTo(6);
        assertThat(primeraVez.get("semillas")).hasSize(1);
        JsonNode semilla = primeraVez.get("semillas").get(0);
        assertThat(semilla.get("roadmapId").asLong()).isEqualTo(501L);
        assertThat(semilla.get("categoria").asText()).isEqualTo("INGENIERIA_SISTEMAS");
        assertThat(semilla.get("titulo").asText()).isEqualTo("Roadmap de sistemas");
        assertThat(semilla.get("obtenidaEn")).isNotNull();

        // la misma llamada otra vez no debe duplicar la semilla
        JsonNode segundaVez = jardin(auth);
        assertThat(segundaVez.get("semillas")).hasSize(1);
        assertThat(segundaVez.get("semillas").get(0).get("id").asLong()).isEqualTo(semilla.get("id").asLong());
    }

    @Test
    void elJardinRespondeAunSinNovedadesDeCursosService() throws Exception {
        String auth = "Bearer " + loginComo(7L);

        JsonNode estado = jardin(auth);

        assertThat(estado.get("gotas").asInt()).isZero();
        assertThat(estado.get("espacios").asInt()).isEqualTo(6);
        assertThat(estado.get("semillas")).isEmpty();
        assertThat(estado.get("plantas")).isEmpty();
    }

    @Test
    void plantarValidaRangoOcupacionYDuenio() throws Exception {
        long usuarioId = 8L;
        when(cursosServiceClient.roadmapsCompletados(eq(usuarioId), anyString())).thenReturn(List.of(
                new RoadmapCompletado(601L, "INGENIERIA_CIVIL", "Roadmap de civil"),
                new RoadmapCompletado(602L, "MATEMATICAS", "Roadmap de matematicas")));
        String auth = "Bearer " + loginComo(usuarioId);
        JsonNode estado = jardin(auth);
        long semillaA = estado.get("semillas").get(0).get("id").asLong();
        long semillaB = estado.get("semillas").get(1).get("id").asLong();

        // fuera de 0..5 es 400, no 500
        plantar(auth, semillaA, 6).andExpect(status().isBadRequest());

        MvcResult plantada = plantar(auth, semillaA, 0).andExpect(status().isOk()).andReturn();
        JsonNode planta = objectMapper.readTree(plantada.getResponse().getContentAsString());
        assertThat(planta.get("espacio").asInt()).isZero();
        assertThat(planta.get("riegos").asInt()).isZero();
        assertThat(planta.get("riegosNecesarios").asInt()).isEqualTo(3);
        assertThat(planta.get("etapa").asText()).isEqualTo("SEMILLA");

        // ya estaba plantada
        plantar(auth, semillaA, 1).andExpect(status().isConflict());
        // el espacio ya esta ocupado
        plantar(auth, semillaB, 0).andExpect(status().isConflict());
        // no existe
        plantar(auth, 999999L, 2).andExpect(status().isNotFound());

        // otro usuario no puede plantar una semilla ajena (se ve como si no existiera)
        String authAjeno = "Bearer " + loginComo(9L);
        plantar(authAjeno, semillaB, 2).andExpect(status().isNotFound());
    }

    @Test
    void regarGastaUnaGotaYAvanzaDeEtapaHastaFlorecer() throws Exception {
        long usuarioId = 10L;
        when(cursosServiceClient.roadmapsCompletados(eq(usuarioId), anyString()))
                .thenReturn(List.of(new RoadmapCompletado(701L, "IDIOMAS", "Roadmap de idiomas")));
        String auth = "Bearer " + loginComo(usuarioId);
        long semillaId = jardin(auth).get("semillas").get(0).get("id").asLong();
        plantar(auth, semillaId, 3).andExpect(status().isOk());

        // sin gotas no se puede regar
        regar(auth, semillaId).andExpect(status().isConflict());

        sumarGota(usuarioId);
        MvcResult primerRiego = regar(auth, semillaId).andExpect(status().isOk()).andReturn();
        JsonNode resultado1 = objectMapper.readTree(primerRiego.getResponse().getContentAsString());
        assertThat(resultado1.get("gotas").asInt()).isZero();
        assertThat(resultado1.get("planta").get("riegos").asInt()).isEqualTo(1);
        assertThat(resultado1.get("planta").get("etapa").asText()).isEqualTo("BROTE");

        sumarGota(usuarioId);
        regar(auth, semillaId).andExpect(status().isOk());
        sumarGota(usuarioId);
        MvcResult tercerRiego = regar(auth, semillaId).andExpect(status().isOk()).andReturn();
        JsonNode resultado3 = objectMapper.readTree(tercerRiego.getResponse().getContentAsString());
        assertThat(resultado3.get("planta").get("riegos").asInt()).isEqualTo(3);
        assertThat(resultado3.get("planta").get("etapa").asText()).isEqualTo("FLORECIDA");
        assertThat(resultado3.get("planta").get("florecidaEn")).isNotNull();

        // florecida: ya no se puede regar mas, y la gota no se gasta
        sumarGota(usuarioId);
        regar(auth, semillaId).andExpect(status().isConflict());
        assertThat(jardin(auth).get("gotas").asInt()).isEqualTo(1);
    }

    @Test
    void regarUnaPlantaAjenaOInexistenteDa404() throws Exception {
        long usuarioId = 13L;
        when(cursosServiceClient.roadmapsCompletados(eq(usuarioId), anyString()))
                .thenReturn(List.of(new RoadmapCompletado(901L, "DERECHO", "Roadmap de derecho")));
        String auth = "Bearer " + loginComo(usuarioId);
        long semillaId = jardin(auth).get("semillas").get(0).get("id").asLong();
        plantar(auth, semillaId, 0).andExpect(status().isOk());

        regar(auth, 999999L).andExpect(status().isNotFound());
        String authAjeno = "Bearer " + loginComo(12L);
        regar(authAjeno, semillaId).andExpect(status().isNotFound());
    }

    @Test
    void dosRiegosSimultaneosConUnaSolaGotaSoloDejanGanarAUno() throws Exception {
        long usuarioId = 11L;
        when(cursosServiceClient.roadmapsCompletados(eq(usuarioId), anyString()))
                .thenReturn(List.of(new RoadmapCompletado(801L, "ADMINISTRACION_EMPRESAS", "Roadmap de administracion")));
        String auth = "Bearer " + loginComo(usuarioId);
        long semillaId = jardin(auth).get("semillas").get(0).get("id").asLong();
        plantar(auth, semillaId, 4).andExpect(status().isOk());
        sumarGota(usuarioId); // exactamente 1 gota para 2 intentos de riego al mismo tiempo

        int hilos = 2;
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch listos = new CountDownLatch(hilos);
        CountDownLatch arrancar = new CountDownLatch(1);
        List<Future<Integer>> resultados = new ArrayList<>();
        for (int i = 0; i < hilos; i++) {
            resultados.add(pool.submit(() -> {
                listos.countDown();
                arrancar.await();
                return regar(auth, semillaId).andReturn().getResponse().getStatus();
            }));
        }
        listos.await();
        arrancar.countDown();
        List<Integer> estados = new ArrayList<>();
        for (Future<Integer> f : resultados) {
            estados.add(f.get(10, TimeUnit.SECONDS));
        }
        pool.shutdown();

        assertThat(estados).containsExactlyInAnyOrder(200, 409);
        JsonNode estadoFinal = jardin(auth);
        assertThat(estadoFinal.get("gotas").asInt()).isZero();
        assertThat(estadoFinal.get("plantas").get(0).get("riegos").asInt()).isEqualTo(1);
    }

    @Test
    void elEndpointInternoDeTriviasGanadasSumaTambienUnaGota() throws Exception {
        long usuarioId = 12L;
        String auth = "Bearer " + loginComo(usuarioId);
        assertThat(jardin(auth).get("gotas").asInt()).isZero();

        mockMvc.perform(post("/api/interno/trivias-ganadas/" + usuarioId).header("X-Internal-Key", llaveInterna))
                .andExpect(status().isNoContent());

        assertThat(jardin(auth).get("gotas").asInt()).isEqualTo(1);
    }

    private org.springframework.test.web.servlet.ResultActions plantar(String auth, long id, Integer espacio) throws Exception {
        return mockMvc.perform(post("/api/jardin/plantas/" + id + "/plantar").header("Authorization", auth)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of("espacio", espacio))));
    }

    private org.springframework.test.web.servlet.ResultActions regar(String auth, long id) throws Exception {
        return mockMvc.perform(post("/api/jardin/plantas/" + id + "/regar").header("Authorization", auth));
    }

    private JsonNode jardin(String auth) throws Exception {
        String cuerpo = mockMvc.perform(get("/api/jardin").header("Authorization", auth))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(cuerpo);
    }

    private void sumarGota(long usuarioId) throws Exception {
        mockMvc.perform(post("/api/interno/trivias-ganadas/" + usuarioId).header("X-Internal-Key", llaveInterna))
                .andExpect(status().isNoContent());
    }

    private String loginComo(long usuarioId) throws Exception {
        String respuesta = mockMvc.perform(post("/api/auth/login").contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("usuarioId", usuarioId))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(respuesta).get("token").asText();
    }
}
