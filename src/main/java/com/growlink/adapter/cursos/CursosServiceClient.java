package com.growlink.adapter.cursos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

// HU-05: le preguntamos a cursos-service si el usuario ya tiene roadmap generado
@Component
public class CursosServiceClient {

    private static final Logger log = LoggerFactory.getLogger(CursosServiceClient.class);

    private final RestClient restClient;

    // ATRIBUTO disponibilidad: con plazos maximos. Sin ellos, si cursos-service se cuelga (no cae: se queda sin
    // contestar) cada Home y cada visita al jardin se quedarian esperando con el. Con ellos, a los pocos segundos se
    // sigue sin la respuesta (tieneRoadmap = false, roadmapsCompletados = vacio) y la persona ve el resto de la pantalla.
    @Autowired
    public CursosServiceClient(RestClient.Builder restClientBuilder,
                                @Value("${growlink.cursos-service.base-url}") String baseUrl,
                                @Value("${growlink.cursos-service.connect-timeout-ms:2000}") int connectTimeoutMs,
                                @Value("${growlink.cursos-service.read-timeout-ms:3000}") int readTimeoutMs) {
        var fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(connectTimeoutMs);
        fabrica.setReadTimeout(readTimeoutMs);
        this.restClient = restClientBuilder.baseUrl(baseUrl).requestFactory(fabrica)
                .requestInterceptor(conIdDePeticion()).build();
    }

    // para las pruebas con MockRestServiceServer, que pone su propia fabrica de peticiones
    CursosServiceClient(RestClient.Builder restClientBuilder, String baseUrl) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).requestInterceptor(conIdDePeticion()).build();
    }

    // ATRIBUTO observabilidad: la llamada a cursos-service lleva el mismo X-Request-Id de la peticion que la origino,
    // asi una sola busqueda en los logs sigue la peticion por los dos servicios
    private static ClientHttpRequestInterceptor conIdDePeticion() {
        return (request, body, execution) -> {
            String id = MDC.get("rid");
            if (id != null && !request.getHeaders().containsKey("X-Request-Id")) {
                request.getHeaders().set("X-Request-Id", id);
            }
            return execution.execute(request, body);
        };
    }

    // false tambien cuando cursos-service no responde: no queremos tumbar el home por eso
    public boolean tieneRoadmap(Long usuarioId, String bearerToken) {
        try {
            restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/api/roadmap/mio")
                            .queryParam("usuarioId", usuarioId)
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, bearerToken)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                return false;
            }
            log.warn("cursos-service respondio {} al consultar el roadmap de usuarioId={}", e.getStatusCode(), usuarioId);
            return false;
        } catch (RestClientException e) {
            log.warn("cursos-service no disponible al consultar el roadmap de usuarioId={}: {}", usuarioId, e.getMessage());
            return false;
        }
    }

    // jardin: una semilla por roadmap completado. Solo nos importan estos 3 campos, aunque cursos-service
    // mande mas (totalCursos, horasTotales, completadoEn)
    public record RoadmapCompletado(Long roadmapId, String categoria, String titulo) {
    }

    // lista vacia tambien si cursos-service no responde: el jardin no se cae por eso
    public List<RoadmapCompletado> roadmapsCompletados(Long usuarioId, String bearerToken) {
        try {
            List<RoadmapCompletado> completados = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/api/roadmap/completados")
                            .queryParam("usuarioId", usuarioId)
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, bearerToken)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<RoadmapCompletado>>() {
                    });
            return completados == null ? List.of() : completados;
        } catch (RestClientException e) {
            log.warn("cursos-service no disponible al consultar roadmaps completados de usuarioId={}: {}",
                    usuarioId, e.getMessage());
            return List.of();
        }
    }
}
