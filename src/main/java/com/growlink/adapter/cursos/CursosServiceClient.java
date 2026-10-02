package com.growlink.adapter.cursos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

// HU-05: le preguntamos a cursos-service si el usuario ya tiene roadmap generado
@Component
public class CursosServiceClient {

    private static final Logger log = LoggerFactory.getLogger(CursosServiceClient.class);

    private final RestClient restClient;

    public CursosServiceClient(RestClient.Builder restClientBuilder,
                                @Value("${growlink.cursos-service.base-url}") String baseUrl) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
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
}
