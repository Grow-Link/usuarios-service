package com.growlink.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

// ATRIBUTO seguridad y observabilidad: /actuator/prometheus le da las metricas a Prometheus, y solo a Prometheus
// (ver TokenDeMetricas). Con el registro de Prometheus de verdad (@AutoConfigureObservability: sin esto, las pruebas
// de Spring Boot apagan la exportacion de metricas) y la cadena de seguridad de verdad.
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureObservability
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:growlinkprometheus;MODE=PostgreSQL",
        "observabilidad.metricas-token=" + MetricasPrometheusTest.TOKEN,
        "observabilidad.instancia=replica-de-prueba-1"
})
class MetricasPrometheusTest {

    static final String TOKEN = "token-del-scrape-de-pruebas";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private MeterRegistry registro;

    @Test
    void conElTokenDelScrapeSaleTodoEnFormatoDePrometheusConSuInstancia() throws Exception {
        // una peticion cualquiera, para que haya latencias que mostrar
        mockMvc.perform(get("/actuator/health"));

        MockHttpServletResponse respuesta = metricas(TOKEN);

        assertThat(respuesta.getStatus()).isEqualTo(200);
        String cuerpo = respuesta.getContentAsString(StandardCharsets.UTF_8);
        // las de negocio y las de seguridad de este servicio...
        assertThat(cuerpo).contains("growlink_sesiones_iniciadas_total{")
                .contains("growlink_seguridad_tokens_invalidos_total{")
                .contains("growlink_seguridad_prohibidos_total{")
                .contains("growlink_http_limite_ip_rechazos_total{");
        // ...todas con la replica que las midio
        assertThat(cuerpo).contains("instancia=\"replica-de-prueba-1\"");
        // y las cubetas de latencia, que es de donde el tablero saca el p95 por replica
        assertThat(cuerpo).contains("http_server_requests_seconds_bucket{").contains("le=\"0.1\"");
    }

    @Test
    void sinTokenOConUnoEquivocadoNoSeVenYQuedaContado() throws Exception {
        double invalidos0 = contador("growlink.seguridad.tokens-invalidos");

        assertThat(mockMvc.perform(get("/actuator/prometheus")).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(metricas("token-que-no-es-el-del-scrape").getStatus()).isEqualTo(401);
        assertThat(metricas(TOKEN.substring(0, TOKEN.length() - 1)).getStatus()).isEqualTo(401);

        // es un evento de seguridad como cualquier otro 401
        assertThat(contador("growlink.seguridad.tokens-invalidos") - invalidos0).isEqualTo(3);
    }

    @Test
    void elTokenDeUnaPersonaNoAbreLasMetricasNiSiendoAdmin() throws Exception {
        // Carla (id 3) es la ADMIN del login de demostracion
        String tokenDeAdmin = loginDeDemostracion(3);

        MockHttpServletResponse respuesta = mockMvc.perform(get("/actuator/prometheus")
                .header("Authorization", "Bearer " + tokenDeAdmin)).andReturn().getResponse();

        assertThat(respuesta.getStatus()).isEqualTo(403);
    }

    @Test
    void lasVariantesDeLaRutaTampocoLasAbren() throws Exception {
        String tokenDeAdmin = loginDeDemostracion(3);

        // con una barra al final, con un parametro de ruta o con mayusculas no hay otra puerta: nunca 200
        for (String ruta : new String[]{"/actuator/prometheus/", "/actuator/prometheus;x=1", "/actuator/Prometheus"}) {
            assertThat(mockMvc.perform(get(ruta)).andReturn().getResponse().getStatus())
                    .as("sin token: " + ruta).isNotEqualTo(200);
            assertThat(mockMvc.perform(get(ruta).header("Authorization", "Bearer " + tokenDeAdmin))
                    .andReturn().getResponse().getStatus())
                    .as("con el token de una persona: " + ruta).isNotEqualTo(200);
        }
    }

    @Test
    void laSaludSigueAbiertaParaElBalanceador() throws Exception {
        assertThat(mockMvc.perform(get("/actuator/health/readiness")).andReturn().getResponse().getStatus())
                .isEqualTo(200);
    }

    private MockHttpServletResponse metricas(String token) throws Exception {
        return mockMvc.perform(get("/actuator/prometheus").header(TokenDeMetricas.CABECERA, token))
                .andReturn().getResponse();
    }

    private String loginDeDemostracion(long usuarioId) throws Exception {
        String cuerpo = mockMvc.perform(post("/api/auth/login").contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("usuarioId", usuarioId))))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(cuerpo).get("token").asText();
    }

    private double contador(String nombre) {
        var contador = registro.find(nombre).counter();
        return contador == null ? 0 : contador.count();
    }
}
