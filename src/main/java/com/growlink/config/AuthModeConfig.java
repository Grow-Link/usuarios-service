package com.growlink.config;

import com.growlink.adapter.security.JwksKeyLocator;
import com.growlink.adapter.security.OidcAuthenticationFilter;
import com.growlink.adapter.security.OidcTokenVerificador;
import com.growlink.adapter.security.ValidadorDeEmisor;
import com.growlink.application.IngresoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

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

    // El JWKS de /common sirve para los dos modos de multi-tenant (es de donde Entra publica las
    // llaves de firma de todos los tenants), por eso es el valor por defecto cuando
    // auth.multi-tenant=true. Igual se puede cambiar con AUTH_JWKS_URI.
    static final String JWKS_DE_ENTRA_COMMON = "https://login.microsoftonline.com/common/discovery/v2.0/keys";

    // La llave publica se lee del JWKS del emisor, no se configura a mano: asi una rotacion de
    // llaves del proveedor no obliga a redeplegar este servicio.
    //
    // De donde sale el JWKS si no se configura auth.oidc.jwks-uri:
    //   multi-tenant=true   el /common de Entra (JWKS_DE_ENTRA_COMMON), que es lo que corresponde
    //                       cuando el emisor no es uno fijo
    //   multi-tenant=false  la ruta estandar de OIDC bajo el emisor. OJO con Entra ID: ahi las
    //                       llaves NO estan en esa ruta, sino en
    //                       https://login.microsoftonline.com/<tenant>/discovery/v2.0/keys, asi que
    //                       con Microsoft y un solo tenant hay que poner AUTH_JWKS_URI
    //
    // Se deja asi, explicito, en vez de leer el documento de descubrimiento del emisor al arrancar:
    // es el mismo trato que en trivia-service, y el servicio no depende de una llamada de red para
    // poder arrancar.
    @Bean
    @ConditionalOnProperty(name = "auth.mode", havingValue = MODO_OIDC)
    public OidcTokenVerificador oidcTokenVerificador(
            @Value("${auth.oidc.issuer-uri:}") String issuerUri,
            @Value("${auth.oidc.audience:}") String audience,
            @Value("${auth.oidc.jwks-uri:}") String jwksUri,
            @Value("${auth.multi-tenant:false}") boolean multiTenant,
            @Value("${auth.allowed-tenants:}") String tenantsPermitidos) {
        // la audiencia no es opcional en ninguno de los dos modos: sin ella sirve el token que el
        // mismo emisor dio para otra aplicacion
        exigir(audience, "auth.oidc.audience (AUTH_AUDIENCE)");
        Set<String> audiencias = lista(audience);

        ValidadorDeEmisor emisor = multiTenant
                ? emisorMultiTenant(issuerUri, tenantsPermitidos)
                : emisorFijo(issuerUri);

        String uriDelJwks = uriDelJwks(jwksUri, issuerUri, multiTenant);
        log.info("modo oidc: multi-tenant={} emisor={} audiencias={} jwks={}", multiTenant,
                multiTenant ? "cualquier tenant v2.0 de Entra ID" + tenantsEnElLog(emisor) : issuerUri,
                audiencias, uriDelJwks);
        return new OidcTokenVerificador(emisor, audiencias, new JwksKeyLocator(uriDelJwks));
    }

    // Un solo directorio: el emisor es obligatorio, porque sin el cualquiera que tenga una llave
    // privada propia puede firmarse un token
    private static ValidadorDeEmisor emisorFijo(String issuerUri) {
        exigir(issuerUri, "auth.oidc.issuer-uri (AUTH_ISSUER_URI)");
        return ValidadorDeEmisor.fijo(issuerUri.trim());
    }

    // Cualquier directorio: el emisor viene en el token y lo valida ValidadorDeEmisor contra el
    // patron de Entra ID, asi que AUTH_ISSUER_URI ya no aplica. Si viene puesto se avisa, porque es
    // justo el tipo de cosa que hace pensar que esta configurado algo que no se esta usando.
    private static ValidadorDeEmisor emisorMultiTenant(String issuerUri, String tenantsPermitidos) {
        if (issuerUri != null && !issuerUri.isBlank()) {
            log.warn("con auth.multi-tenant=true no se usa AUTH_ISSUER_URI ({}): el emisor se valida"
                    + " contra el patron de Entra ID y el tid de cada token", issuerUri);
        }
        Set<String> tenants = lista(tenantsPermitidos);
        // un GUID mal escrito en AUTH_ALLOWED_TENANTS dejaria fuera a un tenant entero sin que nadie
        // se enterara hasta que alguien de esa organizacion no pudiera entrar: mejor no arrancar
        tenants.stream().filter(tenant -> !ValidadorDeEmisor.esGuid(tenant)).findFirst()
                .ifPresent(malo -> {
                    throw new IllegalStateException("auth.allowed-tenants (AUTH_ALLOWED_TENANTS) solo"
                            + " acepta GUID de tenant, y llego \"" + malo + "\"");
                });
        return ValidadorDeEmisor.multiTenant(tenants);
    }

    private static String uriDelJwks(String jwksUri, String issuerUri, boolean multiTenant) {
        if (jwksUri != null && !jwksUri.isBlank()) {
            return jwksUri.trim();
        }
        return multiTenant
                ? JWKS_DE_ENTRA_COMMON
                : issuerUri.replaceAll("/+$", "") + "/.well-known/jwks.json";
    }

    // una lista separada por comas, sin espacios ni vacios, y en el orden en que viene configurada
    private static Set<String> lista(String valores) {
        if (valores == null) {
            return new LinkedHashSet<>();
        }
        return Arrays.stream(valores.split(","))
                .map(String::trim)
                .filter(valor -> !valor.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static String tenantsEnElLog(ValidadorDeEmisor emisor) {
        return emisor.getTenantsPermitidos().isEmpty()
                ? ""
                : " limitado a " + emisor.getTenantsPermitidos();
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
