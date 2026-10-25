package com.growlink.adapter.cursos;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

// ATRIBUTOS de calidad de la llamada de usuarios-service a cursos-service, contra un servidor de verdad (el del JDK):
//  - disponibilidad: si cursos-service se CUELGA (no cae, se queda sin contestar) el Home y el jardin no se cuelgan con el;
//  - observabilidad: la llamada lleva el mismo X-Request-Id de la peticion que la origino.
class CursosServiceClientCalidadTest {

    private HttpServer servidor;
    private volatile String idRecibido;
    private volatile long demoraMs;

    @BeforeEach
    void arrancarCursosDeMentira() throws IOException {
        demoraMs = 0;
        idRecibido = null;
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", intercambio -> {
            idRecibido = intercambio.getRequestHeaders().getFirst("X-Request-Id");
            try {
                Thread.sleep(demoraMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] cuerpo = (intercambio.getRequestURI().getPath().endsWith("completados") ? "[]" : "{\"id\":1,\"cursos\":[]}")
                    .getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            try {
                intercambio.sendResponseHeaders(200, cuerpo.length);
                intercambio.getResponseBody().write(cuerpo);
            } catch (IOException ignorado) {
                // el cliente ya se fue por el plazo: es lo que esta prueba provoca
            } finally {
                intercambio.close();
            }
        });
        servidor.start();
    }

    @AfterEach
    void apagar() {
        servidor.stop(0);
        MDC.clear();
    }

    private CursosServiceClient cliente(int conexionMs, int lecturaMs) {
        return new CursosServiceClient(RestClient.builder(), "http://127.0.0.1:" + servidor.getAddress().getPort(),
                conexionMs, lecturaMs);
    }

    @Test
    void siCursosServiceSeCuelgaSeSigueSinEsperarloDemasiado() {
        demoraMs = 1500; // contesta, pero tarda mucho mas que el plazo (400 ms)
        var client = cliente(300, 400);

        long inicio = System.nanoTime();
        boolean tiene = client.tieneRoadmap(5L, "Bearer x");
        long ms = (System.nanoTime() - inicio) / 1_000_000;

        assertThat(tiene).as("sin respuesta a tiempo se sigue sin el roadmap, no se rompe el Home").isFalse();
        assertThat(ms).as("no esperó a que terminara el servicio colgado").isLessThan(1200);
    }

    @Test
    void elJardinTambienDegradaAListaVaciaEnVezDeColgarse() {
        demoraMs = 1500;
        var client = cliente(300, 400);

        long inicio = System.nanoTime();
        var completados = client.roadmapsCompletados(5L, "Bearer x");
        long ms = (System.nanoTime() - inicio) / 1_000_000;

        assertThat(completados).isEmpty();
        assertThat(ms).isLessThan(1200);
    }

    @Test
    void conUnCursosServiceSanoLosPlazosNoEstorban() {
        var client = cliente(300, 2000);
        assertThat(client.tieneRoadmap(5L, "Bearer x")).isTrue();
        assertThat(client.roadmapsCompletados(5L, "Bearer x")).isEmpty();
    }

    @Test
    void laLlamadaLlevaElIdentificadorDeLaPeticionQueLaOrigino() {
        MDC.put("rid", "abc-12345678");
        cliente(300, 2000).tieneRoadmap(5L, "Bearer x");
        assertThat(idRecibido).isEqualTo("abc-12345678");
    }

    @Test
    void sinPeticionDeOrigenNoSeInventaUnIdentificador() {
        cliente(300, 2000).tieneRoadmap(5L, "Bearer x");
        assertThat(idRecibido).isNull();
    }
}
