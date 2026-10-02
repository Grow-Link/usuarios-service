package com.growlink.adapter.cursos;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CursosServiceClientTest {

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final CursosServiceClient client = new CursosServiceClient(builder, "http://cursos-service");

    @Test
    void devuelveTrueCuandoCursosServiceConfirmaElRoadmap() {
        server.expect(requestTo("http://cursos-service/api/roadmap/mio?usuarioId=5"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token"))
                .andRespond(withSuccess("{\"id\":14,\"usuarioId\":5,\"cursos\":[]}", MediaType.APPLICATION_JSON));

        assertThat(client.tieneRoadmap(5L, "Bearer token")).isTrue();
    }

    @Test
    void devuelveFalseCuandoCursosServiceRespondeNotFound() {
        server.expect(requestTo("http://cursos-service/api/roadmap/mio?usuarioId=5"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(client.tieneRoadmap(5L, "Bearer token")).isFalse();
    }

    @Test
    void devuelveFalseSinTumbarElHomeCuandoCursosServiceFalla() {
        server.expect(requestTo("http://cursos-service/api/roadmap/mio?usuarioId=5"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThat(client.tieneRoadmap(5L, "Bearer token")).isFalse();
    }
}
