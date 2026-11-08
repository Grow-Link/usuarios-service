package com.growlink.adapter.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.LocatorAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.Key;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

// Resuelve la llave publica con la que verificar la firma del JWT de Microsoft, leyendola del JWKS
// que publica el emisor (el endpoint estandar de OIDC, normalmente <issuer>/.well-known/jwks.json
// o, en Entra ID, <issuer>/discovery/v2.0/keys).
//
// Es la misma pieza que ya usa trivia-service, a proposito: los tres servicios validan los tokens
// del mismo proveedor y una puerta mas floja que las otras seria justo la que usaria un atacante.
// Se hace con jjwt (la libreria que ya usa TokenService para el modo legacy) y el HttpClient del
// JDK en vez de meter spring-boot-starter-oauth2-resource-server, para no reescribir la cadena de
// filtros que ya tiene armada SecurityConfig.
//
// Las llaves se cachean porque el emisor las rota pocas veces y ninguna peticion deberia pagar una
// llamada HTTP. Si llega un kid que no esta en el cache se recarga el JWKS (asi una rotacion de
// llaves del emisor no deja a todo el mundo afuera hasta el proximo reinicio), pero no mas seguido
// que REFRESCO_MINIMO: si no, un token con un kid inventado seria un ataque gratis contra el
// emisor, una peticion suya por cada token falso.
//
// Se extiende LocatorAdapter y no se implementa Locator a secas para que esto solo se use con JWS
// (tokens firmados): si llegara un JWE o un JWT sin proteger, el adaptador devuelve null y jjwt lo
// rechaza, en vez de que aqui se intente sacarle un kid a un header que no lo tiene.
public class JwksKeyLocator extends LocatorAdapter<Key> {

    private static final Logger log = LoggerFactory.getLogger(JwksKeyLocator.class);
    private static final Duration REFRESCO_MINIMO = Duration.ofMinutes(1);
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final String jwksUri;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    // el mapa se reemplaza completo en cada recarga, nunca se muta: asi una peticion que esta
    // leyendo no se cruza a mitad de camino con la recarga que disparo otra
    private final AtomicReference<Map<String, PublicKey>> cache = new AtomicReference<>(Map.of());
    private final AtomicLong ultimaRecarga = new AtomicLong(0L);

    public JwksKeyLocator(String jwksUri) {
        this(jwksUri, HttpClient.newBuilder().connectTimeout(TIMEOUT).build());
    }

    // para las pruebas, que le pasan un HttpClient apuntando a su propio JWKS
    JwksKeyLocator(String jwksUri, HttpClient http) {
        this.jwksUri = jwksUri;
        this.http = http;
    }

    @Override
    protected Key locate(JwsHeader header) {
        exigirRs256(header);
        String kid = exigirKid(header);

        PublicKey enCache = cache.get().get(kid);
        if (enCache != null) {
            return enCache;
        }
        if (!puedeRecargar()) {
            throw new TokenInvalidoException(
                    "el kid " + kid + " no esta en el JWKS en cache y todavia no toca recargar");
        }
        recargar();
        PublicKey recienCargada = cache.get().get(kid);
        if (recienCargada == null) {
            throw new TokenInvalidoException("el emisor no publica ninguna llave de firma con kid " + kid);
        }
        return recienCargada;
    }

    // Solo RS256. Esto es lo que cierra la confusion de algoritmo: sin este chequeo, un atacante
    // puede tomar la llave PUBLICA del emisor (que es publica, cualquiera la baja del JWKS), firmar
    // un token con HS256 usando esos mismos bytes como secreto de HMAC, y un verificador descuidado
    // lo validaria contra la misma llave y lo daria por bueno, con el sub que el atacante quiera.
    // Se rechaza antes de verificar la firma, para no depender de que la libreria se niegue a usar
    // una llave RSA para HMAC.
    //
    // El alg=none (el token sin firma) no llega hasta aqui: ese no es un JWS, y el parser de
    // OidcTokenVerificador lo rechaza antes porque no se arma con .unsecured().
    private static void exigirRs256(JwsHeader header) {
        String alg = header.getAlgorithm();
        if (!"RS256".equals(alg)) {
            throw new TokenInvalidoException(
                    "el token viene firmado con " + alg + " y aqui solo se acepta RS256");
        }
    }

    // El kid dice con cual de las llaves del emisor se firmo el token, y es obligatorio: sin el
    // habria que probar la firma contra todas las llaves publicadas, que es justo lo que un
    // verificador no debe hacer (y lo que convierte una rotacion de llaves en un acertijo)
    private static String exigirKid(JwsHeader header) {
        String kid = header.getKeyId();
        if (kid == null || kid.isBlank()) {
            throw new TokenInvalidoException(
                    "el token no trae kid, no se sabe con que llave del emisor se firmo");
        }
        return kid;
    }

    private boolean puedeRecargar() {
        long ahora = System.currentTimeMillis();
        long anterior = ultimaRecarga.get();
        if (anterior != 0L && ahora - anterior < REFRESCO_MINIMO.toMillis()) {
            return false;
        }
        // compareAndSet para que, si llegan varias peticiones a la vez con un kid que no esta en el
        // cache, solo una salga a pedirle el JWKS al emisor y las demas usen lo que esa deje
        return ultimaRecarga.compareAndSet(anterior, ahora);
    }

    private void recargar() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(jwksUri))
                    .timeout(TIMEOUT)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> respuesta = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (respuesta.statusCode() != 200) {
                throw new TokenInvalidoException("el JWKS " + jwksUri + " respondio " + respuesta.statusCode());
            }
            Map<String, PublicKey> llaves = parsear(respuesta.body());
            cache.set(llaves);
            log.info("JWKS de {} cargado, {} llave(s) de firma", jwksUri, llaves.size());
        } catch (TokenInvalidoException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TokenInvalidoException("se interrumpio la lectura del JWKS " + jwksUri, e);
        } catch (Exception e) {
            throw new TokenInvalidoException("no se pudo leer el JWKS " + jwksUri, e);
        }
    }

    private Map<String, PublicKey> parsear(String cuerpo) throws Exception {
        Map<String, PublicKey> llaves = new HashMap<>();
        KeyFactory rsa = KeyFactory.getInstance("RSA");
        Base64.Decoder base64url = Base64.getUrlDecoder();
        JsonNode keys = mapper.readTree(cuerpo).path("keys");
        for (JsonNode jwk : keys) {
            // solo RSA y solo para firma: un JWKS tambien puede traer llaves de cifrado o de otra
            // familia (EC), y usar una de esas para verificar una firma seria un error
            if (!"RSA".equals(jwk.path("kty").asText()) || jwk.path("n").isMissingNode()) {
                continue;
            }
            if (!"sig".equals(jwk.path("use").asText("sig"))) {
                continue;
            }
            // las llaves se indexan por kid y el token tiene que traer el suyo (ver exigirKid), asi
            // que una llave publicada sin kid no se podria elegir nunca: no se guarda
            String kid = jwk.path("kid").asText("");
            if (kid.isBlank()) {
                continue;
            }
            // tampoco sirve una llave que el emisor marca para otro algoritmo: aqui solo RS256
            String alg = jwk.path("alg").asText("RS256");
            if (!"RS256".equals(alg)) {
                continue;
            }
            BigInteger modulo = new BigInteger(1, base64url.decode(jwk.path("n").asText()));
            BigInteger exponente = new BigInteger(1, base64url.decode(jwk.path("e").asText("AQAB")));
            llaves.put(kid, rsa.generatePublic(new RSAPublicKeySpec(modulo, exponente)));
        }
        if (llaves.isEmpty()) {
            throw new TokenInvalidoException(
                    "el JWKS " + jwksUri + " no trae ninguna llave RSA de firma RS256 con kid");
        }
        return Map.copyOf(llaves);
    }
}
