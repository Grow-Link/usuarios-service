package com.growlink.adapter.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Atributos de calidad de usuarios-service con su escenario comprobable (ver infra/docs/atributos-de-calidad.md):
//  - disponibilidad: la salud se consulta sin credenciales y sin filtrar detalles (la usan el balanceador y Azure);
//  - observabilidad: cada peticion lleva un X-Request-Id (se respeta uno valido, se reemplaza uno sospechoso).
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CalidadDeServicioTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void laSaludSeConsultaSinCredencialesYNoFiltraDetalles() throws Exception {
        MvcResult res = mockMvc.perform(get("/actuator/health")).andExpect(status().isOk()).andReturn();
        JsonNode cuerpo = objectMapper.readTree(res.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(cuerpo.get("status").asText()).isEqualTo("UP");
        assertThat(cuerpo.has("components")).isFalse();
        assertThat(res.getResponse().getContentAsString()).doesNotContainIgnoringCase("h2").doesNotContainIgnoringCase("postgres");
    }

    @Test
    void lasDosSondasDelBalanceadorResponden() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    }

    @Test
    void loQueNoSeDecidioExponerNoEstaDisponible() throws Exception {
        for (String ruta : List.of("/actuator/env", "/actuator/beans", "/actuator/heapdump", "/actuator/loggers")) {
            assertThat(mockMvc.perform(get(ruta)).andReturn().getResponse().getStatus()).as(ruta).isNotEqualTo(200);
        }
    }

    @Test
    void cadaRespuestaTraeUnIdentificadorDePeticionNuevo() throws Exception {
        String uno = mockMvc.perform(get("/actuator/health")).andReturn().getResponse().getHeader("X-Request-Id");
        String dos = mockMvc.perform(get("/actuator/health")).andReturn().getResponse().getHeader("X-Request-Id");
        assertThat(UUID.fromString(uno)).isNotNull();
        assertThat(dos).isNotEqualTo(uno);
    }

    @Test
    void siLlegaUnIdentificadorValidoSeRespetaParaSeguirLaPeticionEntreServicios() throws Exception {
        String recibido = mockMvc.perform(get("/actuator/health").header("X-Request-Id", "abc-123_XYZ.456"))
                .andReturn().getResponse().getHeader("X-Request-Id");
        assertThat(recibido).isEqualTo("abc-123_XYZ.456");
    }

    @Test
    void unIdentificadorSospechosoSeReemplazaEnVezDeCopiarloAlLogYALaRespuesta() throws Exception {
        for (String malo : List.of("corto", "tiene espacios y <script>", "x".repeat(200))) {
            String devuelto = mockMvc.perform(get("/actuator/health").header("X-Request-Id", malo))
                    .andReturn().getResponse().getHeader("X-Request-Id");
            assertThat(devuelto).as(malo).isNotEqualTo(malo);
            assertThat(UUID.fromString(devuelto)).isNotNull();
        }
    }

    @Test
    void hastaLasRespuestasRechazadasLlevanElIdentificadorYLaInstancia() throws Exception {
        var res = mockMvc.perform(get("/api/perfil/me")).andExpect(status().is(401)).andReturn().getResponse();
        assertThat(res.getHeader("X-Request-Id")).isNotBlank();
        assertThat(res.getHeader("X-Instancia")).isNotBlank();
    }
}
