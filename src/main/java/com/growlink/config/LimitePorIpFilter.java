package com.growlink.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;

// ATRIBUTO: seguridad y disponibilidad. Un tope por IP a TODAS las peticiones, puesto antes de la
// seguridad y de la base de datos, que es donde sale barato rechazarlas. Sin esto, un script que no
// se identifica puede golpear cualquier endpoint (el login, la busqueda de personas, la salud) y
// dejar al servicio sin hilos ni conexiones para los demas; y en el modo oidc cada peticion con
// token cuesta ademas una verificacion de firma.
//
// Mas de `maximo-por-minuto` peticiones de la misma IP dentro de la ventana reciben 429 con
// Retry-After. La cuenta es por ventana fija (una IP ocupa un contador, no la lista de sus
// peticiones): asi un ataque no puede hacer crecer la memoria pidiendo mas rapido. Cada replica
// cuenta por su lado, el servicio sigue sin estado compartido, asi que con N replicas el tope real
// es de hasta N veces el configurado. maximo-por-minuto: 0 lo apaga.
//
// Va despues de SolicitudIdFilter para que la linea del log salga con el X-Request-Id de la
// peticion: el patron del log del proyecto ya lo imprime en cada linea (%X{rid}, ver
// application.yml), venga del cliente o se haya creado aqui.
//
// Es el mismo filtro que cursos-service, con los mismos nombres de configuracion y de metrica, para
// que el tope se vea igual en los dos servicios y el tablero los pueda sumar.
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class LimitePorIpFilter extends OncePerRequestFilter {

    // La IP de verdad: delante del servicio hay un balanceador, asi que la del socket es la suya, no
    // la del cliente. El primer valor de X-Forwarded-For es el cliente original (los que siguen son
    // los proxies por los que paso).
    public static final String CABECERA_IP = "X-Forwarded-For";

    // tope de IPs vigiladas a la vez: un techo para que una lluvia de IPs distintas no haga crecer
    // la memoria sin limite
    private static final int MAX_IPS = 50_000;

    private final int maximo;
    private final long ventanaMillis;
    private final Clock reloj;
    private final MetricasDeSeguridad metricas;
    private final ConcurrentHashMap<String, Contador> porIp = new ConcurrentHashMap<>();

    @Autowired
    public LimitePorIpFilter(@Value("${seguridad.limite-ip.maximo-por-minuto:300}") int maximo,
                             @Value("${seguridad.limite-ip.ventana-segundos:60}") int ventanaSegundos,
                             MetricasDeSeguridad metricas) {
        this(maximo, ventanaSegundos, metricas, Clock.systemUTC());
    }

    // el reloj se inyecta para poder probar el paso del tiempo sin esperar de verdad
    LimitePorIpFilter(int maximo, int ventanaSegundos, MetricasDeSeguridad metricas, Clock reloj) {
        this.maximo = maximo;
        this.ventanaMillis = Math.max(1L, ventanaSegundos) * 1000L;
        this.metricas = metricas;
        this.reloj = reloj;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (maximo <= 0) {
            filterChain.doFilter(request, response);
            return;
        }
        String ip = ipDe(request);
        long esperaSegundos = segundosDeEsperaSiSePaso(ip);
        if (esperaSegundos < 0) {
            filterChain.doFilter(request, response);
            return;
        }
        metricas.limitePorIp(request.getMethod(), request.getRequestURI(), ip, maximo, ventanaMillis / 1000);

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(esperaSegundos));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        // misma forma que el resto de los errores del servicio (ver GlobalExceptionHandler.ErrorBody)
        response.getWriter().write("{\"message\":\"Demasiadas peticiones desde tu red. Espera "
                + esperaSegundos + " s y vuelve a intentarlo.\"}");
    }

    /** La IP del cliente: el primer valor de X-Forwarded-For si llega (sin puerto), y si no la del socket. */
    static String ipDe(HttpServletRequest request) {
        String reenviada = request.getHeader(CABECERA_IP);
        if (reenviada != null) {
            String primera = sinPuerto(reenviada.split(",")[0].trim());
            if (!primera.isEmpty()) {
                return primera;
            }
        }
        String socket = request.getRemoteAddr();
        return socket == null || socket.isBlank() ? "desconocida" : socket;
    }

    // Azure App Service manda el cliente en X-Forwarded-For como IP:puerto (p.ej. 203.0.113.7:51234), y ese puerto
    // es el de la conexion del cliente: cambia con cada conexion nueva. Si se contara con el puerto, cada conexion
    // seria una "IP" distinta que empieza de cero y el tope no frenaria nunca a nadie. Las formas que pueden llegar:
    //
    //   203.0.113.7             IPv4 sola                        -> tal cual
    //   203.0.113.7:51234       IPv4 con puerto                  -> 203.0.113.7
    //   [2001:db8::1]:51234     IPv6 con puerto (va entre [])    -> 2001:db8::1
    //   [2001:db8::1]           IPv6 entre corchetes, sin puerto -> 2001:db8::1
    //   2001:db8::1             IPv6 sola                        -> tal cual: tiene varios ':' y son de la direccion
    //
    // Un solo ':' solo puede ser el del puerto, porque una IPv6 sin corchetes siempre trae mas de uno. Un valor que
    // queda vacio (p.ej. ":51234") se descarta y se usa la IP del socket, como cuando no llega la cabecera.
    static String sinPuerto(String valor) {
        if (valor.startsWith("[")) {
            int cierre = valor.indexOf(']');
            return cierre > 1 ? valor.substring(1, cierre) : valor;
        }
        int dosPuntos = valor.indexOf(':');
        if (dosPuntos >= 0 && dosPuntos == valor.lastIndexOf(':')) {
            return valor.substring(0, dosPuntos);
        }
        return valor;
    }

    /** Anota la peticion de esa IP: -1 si cabe en la ventana, o los segundos que faltan si ya se paso del tope. */
    private long segundosDeEsperaSiSePaso(String ip) {
        if (porIp.size() > MAX_IPS) {
            descartarVentanasViejas();
        }
        long ahora = reloj.millis();
        Contador contador = porIp.computeIfAbsent(ip, clave -> new Contador(ahora));
        synchronized (contador) {
            if (ahora - contador.inicio >= ventanaMillis) {
                contador.inicio = ahora;
                contador.peticiones = 0;
            }
            contador.peticiones++;
            if (contador.peticiones <= maximo) {
                return -1;
            }
            long faltanMillis = contador.inicio + ventanaMillis - ahora;
            return Math.max(1L, (faltanMillis + 999) / 1000);
        }
    }

    // Limpieza aproximada (no hace falta que sea exacta: es un contador de ritmo, no una cuenta de
    // dinero): se sueltan las IPs cuya ventana ya se cerro y que por tanto empezarian de cero igual.
    private void descartarVentanasViejas() {
        long limite = reloj.millis() - ventanaMillis;
        porIp.entrySet().removeIf(entrada -> entrada.getValue().inicio < limite);
    }

    private static final class Contador {
        private long inicio;
        private int peticiones;

        private Contador(long inicio) {
            this.inicio = inicio;
        }
    }
}
