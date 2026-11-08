package com.growlink.config;

import com.growlink.adapter.security.JwksKeyLocator;
import com.growlink.adapter.security.OidcAuthenticationFilter;
import com.growlink.adapter.security.OidcTokenVerificador;
import com.growlink.application.IngresoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// El interruptor auth.mode, el mismo que trivia-service y cursos-service:
//
//   legacy (por defecto) -> no se crea ningun bean de aqui. Sigue andando el login quemado
//                           (/api/auth/login) y los tokens HS256 que firma TokenService con el
//                           secreto compartido. Nada de lo que ya funciona cambia.
//   oidc                 -> la identidad la pone Microsoft: OidcAuthenticationFilter valida el
//                           token contra el JWKS del emisor y traduce su sub a un usuario local
//                           (ver IngresoService).
//
// El modo se valida al arrancar: un valor mal escrito (un "OIDC " con espacio, un "oauth") tiene
// que tumbar el arranque, no quedarse callado cayendo a legacy, porque eso seria desplegar con el
// login viejo creyendo que ya esta puesto el de Microsoft.
@Configuration
public class AuthModeConfig {

    public static final String MODO_LEGACY = "legacy";
    public static final String MODO_OIDC = "oidc";

    private static final Logger log = LoggerFactory.getLogger(AuthModeConfig.class);

    public AuthModeConfig(@Value("${auth.mode:legacy}") String modo) {
        if (!MODO_LEGACY.equals(modo) && !MODO_OIDC.equals(modo)) {
            throw new IllegalStateException(
                    "auth.mode debe ser \"" + MODO_LEGACY + "\" u \"" + MODO_OIDC + "\", llego \"" + modo + "\"");
        }
        log.info("auth.mode={} ({})", modo, MODO_OIDC.equals(modo)
                ? "las peticiones exigen un JWT valido de Microsoft"
                : "sigue el login quemado con tokens propios");
    }

    // La llave publica se lee del JWKS del emisor, no se configura a mano: asi una rotacion de
    // llaves del proveedor no obliga a redeplegar este servicio.
    //
    // Si no se configura auth.oidc.jwks-uri se asume la ruta estandar de OIDC bajo el emisor. OJO
    // con Entra ID: ahi las llaves NO estan en esa ruta, sino en
    // https://login.microsoftonline.com/<tenant>/discovery/v2.0/keys, asi que con Microsoft hay que
    // poner AUTH_JWKS_URI (esta en el README). Se deja asi, explicito, en vez de leer el documento
    // de descubrimiento del emisor al arrancar: es el mismo trato que en trivia-service, y el
    // servicio no depende de una llamada de red para poder arrancar.
    @Bean
    @ConditionalOnProperty(name = "auth.mode", havingValue = MODO_OIDC)
    public OidcTokenVerificador oidcTokenVerificador(
            @Value("${auth.oidc.issuer-uri:}") String issuerUri,
            @Value("${auth.oidc.audience:}") String audience,
            @Value("${auth.oidc.jwks-uri:}") String jwksUri) {
        // en modo oidc estas dos no son opcionales: sin emisor, cualquiera que tenga una llave
        // privada propia puede firmarse un token; sin audiencia sirve el token que el mismo emisor
        // dio para otra aplicacion del mismo tenant
        exigir(issuerUri, "auth.oidc.issuer-uri (AUTH_ISSUER_URI)");
        exigir(audience, "auth.oidc.audience (AUTH_AUDIENCE)");

        String uriDelJwks = (jwksUri == null || jwksUri.isBlank())
                ? issuerUri.replaceAll("/+$", "") + "/.well-known/jwks.json"
                : jwksUri.trim();
        log.info("modo oidc: emisor={} audiencia={} jwks={}", issuerUri, audience, uriDelJwks);
        return new OidcTokenVerificador(issuerUri.trim(), audience.trim(), new JwksKeyLocator(uriDelJwks));
    }

    @Bean
    @ConditionalOnProperty(name = "auth.mode", havingValue = MODO_OIDC)
    public OidcAuthenticationFilter oidcAuthenticationFilter(OidcTokenVerificador verificador,
                                                             IngresoService ingresoService,
                                                             MetricasDeSeguridad metricas) {
        return new OidcAuthenticationFilter(verificador, ingresoService, metricas);
    }

    // Spring Boot registra solo, en la cadena del contenedor, cualquier bean que sea un Filter. Este
    // no lo queremos ahi: tiene que correr DENTRO de la cadena de Spring Security, donde lo mete
    // SecurityConfig, porque es ahi donde se decide quien necesita sesion y quien no. Con esta
    // registracion apagada, Boot ya no lo agrega por su cuenta.
    @Bean
    @ConditionalOnProperty(name = "auth.mode", havingValue = MODO_OIDC)
    public FilterRegistrationBean<OidcAuthenticationFilter> noRegistrarOidcFilterDosVeces(
            OidcAuthenticationFilter filtro) {
        FilterRegistrationBean<OidcAuthenticationFilter> registro = new FilterRegistrationBean<>(filtro);
        registro.setEnabled(false);
        return registro;
    }

    private static void exigir(String valor, String nombre) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("con auth.mode=" + MODO_OIDC + " hay que configurar " + nombre);
        }
    }
}
