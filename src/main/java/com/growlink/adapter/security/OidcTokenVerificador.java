package com.growlink.adapter.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;

import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Set;

// Verificacion del JWT que emite Microsoft (Entra ID) cuando auth.mode=oidc. Por aqui pasan todas
// las peticiones autenticadas del servicio, a traves de OidcAuthenticationFilter.
//
// Que se revisa, y por que cada cosa:
//   firma     con la llave publica que el emisor publica en su JWKS, y SOLO con RS256 (ver
//             JwksKeyLocator): aceptar otros algoritmos abre la puerta a la confusion de
//             algoritmo, que es el ataque clasico contra los verificadores de JWT
//   kid       el token tiene que decir con cual de las llaves del emisor se firmo (JwksKeyLocator)
//   exp       y tiene que traerlo: un token sin exp no vence NUNCA, y uno robado serviria para siempre
//   iss       la firma solo dice "esto lo firmo alguien con esa llave privada", no "lo firmo quien
//             yo espero": sin iss entraria un token valido de otro proveedor. Quien decide si el
//             emisor sirve es ValidadorDeEmisor, porque eso depende de si la app esta registrada
//             para un solo directorio o para cualquiera (ver auth.multi-tenant)
//   aud       sin aud serviria el access token que el MISMO emisor le dio a la persona para
//             cualquier otra aplicacion. Se acepta una lista: segun como quede configurada la app,
//             Entra pone api://<client-id> o el <client-id> pelado
//   sub       la identidad de la persona, y de ningun otro lado
//
// Es aparte de TokenService a proposito: TokenService emite y valida los tokens HS256 del modo
// legacy (el login quemado, con un secreto compartido), y esta clase valida los RS256 de un emisor
// externo, que firma con su llave privada y de quien aqui solo se conoce la publica. Ninguna de las
// dos sabe de la otra.
//
// Diferencia con el mismo verificador de trivia-service y cursos-service: ahi el sub ya es el id
// numerico de GrowLink, porque ellos reciben tokens de un padron que este servicio resuelve. Aqui
// el sub es el identificador OPACO de Microsoft, y es justo este servicio el que lo traduce a un
// usuario local (ver IngresoService), asi que la identidad se devuelve tal como viene en el token.
public class OidcTokenVerificador {

    private static final String PREFIX = "Bearer ";

    // De donde sale el correo. Entra ID manda "email" cuando la cuenta lo tiene publicado y
    // "preferred_username" casi siempre; el orden es el del enunciado. Puede no venir ninguno
    // (una cuenta sin correo verificado), y eso NO invalida el token: la persona entra igual y el
    // usuario local se crea sin correo (ver IngresoService).
    private static final String CLAIM_CORREO = "email";
    private static final String CLAIM_CORREO_ALTERNO = "preferred_username";
    private static final String CLAIM_NOMBRE = "name";

    private final JwtParser parser;
    private final ValidadorDeEmisor emisor;
    private final Set<String> audiencias;

    // Lo que el token dice de la persona. El sub es la llave con la que se busca (o se crea) el
    // usuario local; correo y nombre pueden venir vacios.
    public record Identidad(String sub, String correo, String nombre) {
    }

    public OidcTokenVerificador(ValidadorDeEmisor emisor, Set<String> audiencias, JwksKeyLocator keyLocator) {
        // parser SIN .unsecured(): asi jjwt rechaza por su cuenta los JWT con alg=none, que es el
        // otro ataque clasico (quitarle la firma al token y que el verificador lo acepte igual)
        this(emisor, audiencias, Jwts.parser().keyLocator(keyLocator).build());
    }

    // el caso simple, un emisor fijo y una sola audiencia
    public OidcTokenVerificador(String issuer, String audience, JwksKeyLocator keyLocator) {
        this(ValidadorDeEmisor.fijo(issuer), Set.of(audience), keyLocator);
    }

    // para las pruebas, que le pasan un parser ya armado
    OidcTokenVerificador(ValidadorDeEmisor emisor, Set<String> audiencias, JwtParser parser) {
        this.emisor = emisor;
        // se guarda en orden para que el motivo del rechazo salga en el log igual que viene
        // configurado AUTH_AUDIENCE, que es como lo va a leer quien tenga que corregirlo
        this.audiencias = Collections.unmodifiableSet(new LinkedHashSet<>(audiencias));
        this.parser = parser;
    }

    // Recibe el header Authorization completo ("Bearer ..."). Cualquier problema sale como
    // TokenInvalidoException, con el motivo exacto en el mensaje para que quede en el log del
    // servidor; quien llama (OidcAuthenticationFilter) lo convierte en 401.
    public Identidad identidadDe(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            throw new TokenInvalidoException("la peticion no trae token");
        }
        if (!authorization.startsWith(PREFIX)) {
            throw new TokenInvalidoException("el token no viene como \"Bearer <jwt>\"");
        }
        Claims claims = leer(authorization.substring(PREFIX.length()).trim());
        exigirVencimiento(claims);
        exigirEmisor(claims);
        exigirAudiencia(claims);
        return new Identidad(exigirSub(claims), correoDe(claims), textoDe(claims, CLAIM_NOMBRE));
    }

    // jjwt ya rechaza por su cuenta el alg=none, la firma que no cuadra y el token vencido; los
    // casos se separan solo para que el log diga cual de todos fue
    private Claims leer(String jwt) {
        try {
            return parser.parseSignedClaims(jwt).getPayload();
        } catch (ExpiredJwtException e) {
            throw new TokenInvalidoException("el token esta vencido", e);
        } catch (TokenInvalidoException e) {
            throw e; // la tira JwksKeyLocator (kid o algoritmo), ya trae su propio motivo
        } catch (Exception e) {
            throw new TokenInvalidoException("el token no es un JWS firmado y valido: " + e.getMessage(), e);
        }
    }

    // jjwt solo compara exp si el claim viene; un token sin exp no vence nunca, asi que aqui se
    // exige. Un emisor de OIDC siempre lo pone, de modo que esto no rechaza nada legitimo
    private void exigirVencimiento(Claims claims) {
        Date exp = claims.getExpiration();
        if (exp == null) {
            throw new TokenInvalidoException("el token no trae exp, no venceria nunca");
        }
    }

    // Un emisor fijo o cualquier tenant de Entra ID, segun auth.multi-tenant: la decision esta en
    // ValidadorDeEmisor, que es lo unico que cambia entre los dos modos
    private void exigirEmisor(Claims claims) {
        emisor.exigir(claims);
    }

    // El token pasa si su aud coincide con ALGUNA de las configuradas. Son varias porque, segun
    // como quede registrada la aplicacion, Entra pone api://<client-id> o el <client-id> pelado, y
    // tener que adivinar cual de los dos obligaria a redeplegar para corregirlo.
    private void exigirAudiencia(Claims claims) {
        if (audiencias.isEmpty()) {
            return; // sin audiencia configurada no hay nada que comparar (AuthModeConfig la exige al arrancar)
        }
        Set<String> delToken = claims.getAudience();
        if (delToken == null || audiencias.stream().noneMatch(delToken::contains)) {
            throw new TokenInvalidoException(
                    "el token no tiene ninguna de las audiencias " + audiencias + ", trae " + delToken);
        }
    }

    // Sin sub no se sabe de quien es el token, y es el campo con el que se busca el usuario local:
    // un token asi no se puede atender ni creando nada
    private String exigirSub(Claims claims) {
        String sub = claims.getSubject();
        if (sub == null || sub.isBlank()) {
            throw new TokenInvalidoException("el token no trae sub, no se sabe que persona es");
        }
        return sub.trim();
    }

    private String correoDe(Claims claims) {
        String correo = textoDe(claims, CLAIM_CORREO);
        return correo != null ? correo : textoDe(claims, CLAIM_CORREO_ALTERNO);
    }

    private static String textoDe(Claims claims, String claim) {
        Object valor = claims.get(claim);
        if (valor == null) {
            return null;
        }
        String texto = valor.toString().trim();
        return texto.isEmpty() ? null : texto;
    }
}
