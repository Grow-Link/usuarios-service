package com.growlink;

import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

// Un Microsoft de mentiras para las pruebas del modo auth.mode=oidc: genera un par de llaves RSA en
// el momento (nada de llaves de verdad ni de secretos guardados en el repo), publica la publica en
// un JWKS servido por un HttpServer del JDK, y firma tokens con la privada.
//
// El emisor (issuer) ES la direccion de ese servidor, asi la prueba tambien ejercita la ruta por
// defecto que arma AuthModeConfig cuando no se configura auth.oidc.jwks-uri:
// <issuer>/.well-known/jwks.json.
//
// Tambien hay un segundo par de llaves, el "impostor": sirve para comprobar que un token con pinta
// perfecta pero firmado por otro no entra.
public final class LlavesDePrueba {

    public static final String AUDIENCE = "api://growlink-usuarios";
    public static final String KID = "llave-de-pruebas-1";

    private static final KeyPair PAR = generarPar();
    private static final KeyPair PAR_IMPOSTOR = generarPar();

    private static HttpServer servidor;
    private static String issuer;

    private LlavesDePrueba() {
    }

    // arranca el emisor la primera vez que alguien lo pide y lo deja arriba para toda la corrida; el
    // HttpServer es un daemon de su propio pool, no deja la JVM colgada al terminar
    public static synchronized String issuer() {
        if (servidor == null) {
            try {
                servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            } catch (Exception e) {
                throw new IllegalStateException("no se pudo levantar el emisor de pruebas", e);
            }
            servidor.createContext("/.well-known/jwks.json", intercambio -> {
                byte[] cuerpo = jwksJson().getBytes(StandardCharsets.UTF_8);
                intercambio.getResponseHeaders().add("Content-Type", "application/json");
                intercambio.sendResponseHeaders(200, cuerpo.length);
                try (OutputStream salida = intercambio.getResponseBody()) {
                    salida.write(cuerpo);
                }
            });
            servidor.start();
            issuer = "http://127.0.0.1:" + servidor.getAddress().getPort();
        }
        return issuer;
    }

    public static String jwksUri() {
        return issuer() + "/.well-known/jwks.json";
    }

    // el JWKS tal como lo publicaria cualquier proveedor de OIDC: modulo y exponente en base64url
    public static String jwksJson() {
        RSAPublicKey publica = (RSAPublicKey) PAR.getPublic();
        return "{\"keys\":[{"
                + "\"kty\":\"RSA\","
                + "\"use\":\"sig\","
                + "\"alg\":\"RS256\","
                + "\"kid\":\"" + KID + "\","
                + "\"n\":\"" + base64url(publica.getModulus().toByteArray()) + "\","
                + "\"e\":\"" + base64url(publica.getPublicExponent().toByteArray()) + "\""
                + "}]}";
    }

    // ---- tokens que deben pasar ----

    /** Lo normal: sub, correo en el claim email y nombre. */
    public static String tokenValido(String sub, String correo, String nombre) {
        return new Token(sub).correo(correo).nombre(nombre).firmar(PAR);
    }

    /** Entra ID no siempre publica "email": muchas veces el correo solo viene en preferred_username. */
    public static String tokenConPreferredUsername(String sub, String correo, String nombre) {
        return new Token(sub).preferredUsername(correo).nombre(nombre).firmar(PAR);
    }

    /** Una cuenta sin correo publicado: el token es valido igual y la persona tiene que poder entrar. */
    public static String tokenSinCorreo(String sub, String nombre) {
        return new Token(sub).nombre(nombre).firmar(PAR);
    }

    /** Sin correo y sin nombre: lo minimo con lo que se puede crear un usuario local. */
    public static String tokenSoloConSub(String sub) {
        return new Token(sub).firmar(PAR);
    }

    // ---- tokens que no deben pasar ----

    // vencido hace un minuto: jjwt no aplica tolerancia de reloj por defecto, asi que esto basta
    public static String tokenVencido(String sub) {
        return new Token(sub).vence(Instant.now().minusSeconds(60)).firmar(PAR);
    }

    // sin exp: un token asi no venceria nunca, y uno robado serviria para siempre
    public static String tokenSinVencimiento(String sub) {
        return new Token(sub).vence(null).firmar(PAR);
    }

    public static String tokenDeOtroEmisor(String sub) {
        return new Token(sub).emisor("https://login.microsoftonline.com/otro-tenant/v2.0").firmar(PAR);
    }

    public static String tokenParaOtraAudiencia(String sub) {
        return new Token(sub).audiencia("api://otro-servicio").firmar(PAR);
    }

    // mismo kid, mismo emisor, misma audiencia, todo bien... pero firmado con otra llave privada
    public static String tokenFirmadoPorUnImpostor(String sub) {
        return new Token(sub).firmar(PAR_IMPOSTOR);
    }

    public static String tokenSinKid(String sub) {
        return new Token(sub).kid(null).firmar(PAR);
    }

    public static String tokenConUnKidQueNoExiste(String sub) {
        return new Token(sub).kid("kid-inventado").firmar(PAR);
    }

    public static String tokenSinSub() {
        return new Token(null).firmar(PAR);
    }

    // ---- los dos ataques clasicos contra un verificador de JWT ----

    // alg=none: el token sin firma. Se arma a mano (jjwt no deja firmar "con nada" sin pedirlo
    // aparte) para que sea exactamente lo que mandaria un atacante: header, claims y una tercera
    // parte vacia. Todo lo demas cuadra: emisor, audiencia, kid y un exp del futuro
    public static String tokenConAlgNone(String sub) {
        String header = "{\"alg\":\"none\",\"typ\":\"JWT\",\"kid\":\"" + KID + "\"}";
        String claims = "{\"iss\":\"" + issuer() + "\",\"aud\":\"" + AUDIENCE + "\","
                + "\"sub\":\"" + sub + "\",\"exp\":" + enUnRato().getEpochSecond() + "}";
        return base64url(header.getBytes(StandardCharsets.UTF_8)) + "."
                + base64url(claims.getBytes(StandardCharsets.UTF_8)) + ".";
    }

    // HS256 con un secreto cualquiera: el token no tiene nada que ver con el emisor, pero un
    // verificador que no fije el algoritmo podria intentar validarlo igual
    public static String tokenConHs256(String sub) {
        byte[] secreto = new byte[32];
        new SecureRandom().nextBytes(secreto);
        return hs256(sub, Keys.hmacShaKeyFor(secreto));
    }

    // La confusion de algoritmo, el ataque que de verdad importa: la llave PUBLICA del emisor es
    // publica (cualquiera la baja del JWKS), asi que el atacante la usa como secreto de HMAC y firma
    // un token con el sub que quiera. Un verificador que acepte cualquier algoritmo lo valida contra
    // esa misma llave y lo da por bueno.
    public static String tokenConHs256FirmadoConLaLlavePublica(String sub) {
        return hs256(sub, Keys.hmacShaKeyFor(PAR.getPublic().getEncoded()));
    }

    private static String hs256(String sub, SecretKey secreto) {
        return Jwts.builder()
                .header().keyId(KID).and()
                .issuer(issuer())
                .audience().add(AUDIENCE).and()
                .subject(sub)
                .issuedAt(Date.from(Instant.now().minusSeconds(5)))
                .expiration(Date.from(enUnRato()))
                .signWith(secreto, Jwts.SIG.HS256)
                .compact();
    }

    // Un token armable pieza por pieza: por defecto todo cuadra (emisor, audiencia, kid y un exp del
    // futuro) y cada prueba estropea solo lo que quiere probar.
    private static final class Token {
        private final String sub;
        private String emisor = issuer();
        private String audiencia = AUDIENCE;
        private String kid = KID;
        private Instant vence = enUnRato();
        private String correo;
        private String preferredUsername;
        private String nombre;

        private Token(String sub) {
            this.sub = sub;
        }

        private Token emisor(String emisor) {
            this.emisor = emisor;
            return this;
        }

        private Token audiencia(String audiencia) {
            this.audiencia = audiencia;
            return this;
        }

        private Token kid(String kid) {
            this.kid = kid;
            return this;
        }

        private Token vence(Instant vence) {
            this.vence = vence;
            return this;
        }

        private Token correo(String correo) {
            this.correo = correo;
            return this;
        }

        private Token preferredUsername(String preferredUsername) {
            this.preferredUsername = preferredUsername;
            return this;
        }

        private Token nombre(String nombre) {
            this.nombre = nombre;
            return this;
        }

        private String firmar(KeyPair par) {
            var builder = Jwts.builder()
                    .header().keyId(kid).and()
                    .issuer(emisor)
                    .audience().add(audiencia).and()
                    .issuedAt(Date.from(Instant.now().minusSeconds(5)));
            if (vence != null) {
                builder.expiration(Date.from(vence));
            }
            if (sub != null) {
                builder.subject(sub);
            }
            if (correo != null) {
                builder.claim("email", correo);
            }
            if (preferredUsername != null) {
                builder.claim("preferred_username", preferredUsername);
            }
            if (nombre != null) {
                builder.claim("name", nombre);
            }
            return builder.signWith(par.getPrivate(), Jwts.SIG.RS256).compact();
        }
    }

    private static Instant enUnRato() {
        return Instant.now().plusSeconds(600);
    }

    private static String base64url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static KeyPair generarPar() {
        try {
            KeyPairGenerator generador = KeyPairGenerator.getInstance("RSA");
            generador.initialize(2048);
            return generador.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("no se pudo generar el par de llaves RSA de prueba", e);
        }
    }
}
