package com.growlink.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// ATRIBUTO: seguridad y observabilidad. Los tres eventos de seguridad del servicio, contados y
// escritos en el log en un solo sitio, para que no quede ninguno que pase callado:
//
//   token invalido  un token que no paso la verificacion (401). Sin esto, un emisor o una
//                   audiencia mal configurados en un ambiente son imposibles de diagnosticar:
//                   el cliente solo ve un 401 pelado.
//   limite por IP   una peticion rechazada por el tope por IP (429). El contador dice cuanto
//                   abuso se esta frenando, que es justo lo que no se ve en los logs de acceso.
//   prohibido       una peticion autenticada que pidio algo que no le toca (403): pedir el rol
//                   ADMIN sin estar en la lista, una llave interna que no cuadra, o una regla de
//                   autorizacion que dijo no.
//
// Una subida sostenida de cualquiera de los tres es la senal de que alguien esta probando la
// puerta. Todas las lineas salen con el X-Request-Id de la peticion, porque el patron del log del
// proyecto lo imprime en cada linea (%X{rid}, ver application.yml y SolicitudIdFilter): asi un
// rechazo se puede cruzar con el resto de la peticion, aqui y en los otros servicios.
//
// Nunca se escribe el token en el log: aunque este vencido o mal firmado, un token en el log es
// una credencial en el log.
@Component
public class MetricasDeSeguridad {

    private static final Logger log = LoggerFactory.getLogger(MetricasDeSeguridad.class);

    private final Counter tokensInvalidos;
    private final Counter rechazosPorIp;
    private final Counter prohibidos;

    public MetricasDeSeguridad(MeterRegistry registro) {
        this.tokensInvalidos = Counter.builder("growlink.seguridad.tokens-invalidos")
                .description("peticiones rechazadas con 401 porque el token no paso la verificacion")
                .register(registro);
        // mismo nombre que en cursos-service, para que el tablero sume los dos servicios
        this.rechazosPorIp = Counter.builder("growlink.http.limite-ip.rechazos")
                .description("peticiones rechazadas con 429 por el tope por IP")
                .register(registro);
        this.prohibidos = Counter.builder("growlink.seguridad.prohibidos")
                .description("peticiones rechazadas con 403: pidieron algo que no les toca")
                .register(registro);
    }

    /** Un token que no paso la verificacion. El motivo lo trae TokenInvalidoException. */
    public void tokenInvalido(String metodo, String ruta, String motivo) {
        tokensInvalidos.increment();
        log.warn("Token rechazado en {} {}: {}", metodo, ruta, motivo);
    }

    /** Una peticion que se paso del tope por IP. */
    public void limitePorIp(String metodo, String ruta, String ip, int maximo, long ventanaSegundos) {
        rechazosPorIp.increment();
        log.warn("Limite por IP: se rechaza {} {} de la IP {} (mas de {} peticiones en {} s)",
                metodo, ruta, ip, maximo, ventanaSegundos);
    }

    /** Una peticion autenticada que pidio algo que no le corresponde. */
    public void prohibido(String metodo, String ruta, String motivo) {
        prohibidos.increment();
        log.warn("Acceso prohibido en {} {}: {}", metodo, ruta, motivo);
    }
}
