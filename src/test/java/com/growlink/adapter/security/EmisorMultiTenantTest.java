package com.growlink.adapter.security;

import com.growlink.LlavesDePrueba;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// La app esta registrada en Entra ID como multi-tenant (cuentas de cualquier directorio y cuentas
// personales de Microsoft), asi que el iss del token trae el tenant de QUIEN inicia sesion. Lo que
// se prueba aqui es que eso se acepte SIN aflojar la validacion del emisor.
//
// Todo con las llaves RSA que genera la prueba y su JWKS local (ver LlavesDePrueba): el iss del
// token dice login.microsoftonline.com, pero las llaves se bajan del servidor de la prueba, asi que
// esto no sale a internet ni depende de Microsoft.
class EmisorMultiTenantTest {

    // dos organizaciones distintas, las dos legitimas
    private static final String TENANT_A = "11111111-2222-3333-4444-555555555555";
    private static final String TENANT_B = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";

    // el tenant de las cuentas personales de Microsoft, que es un GUID como cualquier otro
    private static final String TENANT_CUENTAS_PERSONALES = "9188040d-6c67-4c5b-b112-36a304b66dad";

    // uno nuevo por prueba: JwksKeyLocator cachea y limita las recargas, y con un cache compartido
    // el resultado de una prueba dependeria del orden en que corrieron las demas
    private OidcTokenVerificador multiTenant(String... tenantsPermitidos) {
        return new OidcTokenVerificador(ValidadorDeEmisor.multiTenant(List.of(tenantsPermitidos)),
                Set.of(LlavesDePrueba.AUDIENCE), new JwksKeyLocator(LlavesDePrueba.jwksUri()));
    }

    // ---- cualquier tenant entra, si el emisor y el tid cuadran ----

    @Test
    void unTokenDelTenantAEntra() {
        var identidad = multiTenant().identidadDe("Bearer "
                + LlavesDePrueba.tokenDeTenant(TENANT_A, "sub-de-a", "ana@empresa-a.com", "Ana Torres"));

        assertThat(identidad.sub()).isEqualTo("sub-de-a");
        assertThat(identidad.correo()).isEqualTo("ana@empresa-a.com");
    }

    @Test
    void unTokenDelTenantBTambienEntra() {
        var identidad = multiTenant().identidadDe("Bearer "
                + LlavesDePrueba.tokenDeTenant(TENANT_B, "sub-de-b", "beto@empresa-b.com", "Beto Ramirez"));

        assertThat(identidad.sub()).isEqualTo("sub-de-b");
        assertThat(identidad.correo()).isEqualTo("beto@empresa-b.com");
    }

    // el mismo verificador atiende a las dos organizaciones a la vez, que es justo el punto
    @Test
    void elMismoVerificadorAtiendeALasDosOrganizaciones() {
        OidcTokenVerificador unoSolo = multiTenant();

        assertThat(unoSolo.identidadDe("Bearer " + LlavesDePrueba.tokenDeTenant(TENANT_A, "a", null, null)).sub())
                .isEqualTo("a");
        assertThat(unoSolo.identidadDe("Bearer " + LlavesDePrueba.tokenDeTenant(TENANT_B, "b", null, null)).sub())
                .isEqualTo("b");
    }

    // las cuentas personales de Microsoft vienen con el tenant de MSA y entran por el mismo camino;
    // ademas suelen traer el correo solo en preferred_username, no en email
    @Test
    void unaCuentaPersonalDeMicrosoftEntraPorElMismoCamino() {
        var identidad = multiTenant().identidadDe("Bearer "
                + LlavesDePrueba.tokenDeTenant(TENANT_CUENTAS_PERSONALES, "sub-personal", null, "Alguien"));

        assertThat(identidad.sub()).isEqualTo("sub-personal");
    }

    // ---- el tid tiene que ser el tenant que firma ----

    // Sin esto, el tenant del que habla el token y el que lo firma podrian no ser el mismo, y el tid
    // es el dato con el que una aplicacion decide de que organizacion es la persona
    @Test
    void unTidDistintoAlDelEmisorNoPasa() {
        String token = LlavesDePrueba.tokenConEmisorYTid(
                LlavesDePrueba.emisorDeTenant(TENANT_A), TENANT_B, "sub-mentiroso");

        assertThatThrownBy(() -> multiTenant().identidadDe("Bearer " + token))
                .isInstanceOf(TokenInvalidoException.class)
                .hasMessageContaining("tid");
    }

    @Test
    void unTokenSinTidNoPasaEnMultiTenant() {
        // emisor valido, pero el token no dice de que organizacion es
        String token = LlavesDePrueba.tokenConEmisorYTid(
                LlavesDePrueba.emisorDeTenant(TENANT_A), null, "sub-sin-tid");

        assertThatThrownBy(() -> multiTenant().identidadDe("Bearer " + token))
                .isInstanceOf(TokenInvalidoException.class)
                .hasMessageContaining("tid");
    }

    // ---- los emisores parecidos, que es el ataque de este modo ----

    @Test
    void unEmisorConUnHostParecidoAlDeMicrosoftNoPasa() {
        // a la vista pasa por el bueno, y con una comparacion floja ("empieza por", "contiene") entraria
        for (String emisor : List.of(
                "https://login.microsoftonline.com.evil.com/" + TENANT_A + "/v2.0",
                "https://evil.com/login.microsoftonline.com/" + TENANT_A + "/v2.0",
                "https://login-microsoftonline.com/" + TENANT_A + "/v2.0",
                "https://login.microsoftonline.evil.com/" + TENANT_A + "/v2.0")) {
            String token = LlavesDePrueba.tokenConEmisorYTid(emisor, TENANT_A, "sub-del-atacante");

            assertThatThrownBy(() -> multiTenant().identidadDe("Bearer " + token))
                    .as(emisor)
                    .isInstanceOf(TokenInvalidoException.class)
                    .hasMessageContaining("emisor");
        }
    }

    @Test
    void unEmisorEnHttpNoPasa() {
        String token = LlavesDePrueba.tokenConEmisorYTid(
                "http://login.microsoftonline.com/" + TENANT_A + "/v2.0", TENANT_A, "sub");

        assertThatThrownBy(() -> multiTenant().identidadDe("Bearer " + token))
                .isInstanceOf(TokenInvalidoException.class)
                .hasMessageContaining("emisor");
    }

    @Test
    void unEmisorSinV2NoPasa() {
        for (String emisor : List.of(
                "https://login.microsoftonline.com/" + TENANT_A,
                "https://login.microsoftonline.com/" + TENANT_A + "/",
                "https://login.microsoftonline.com/" + TENANT_A + "/v1.0",
                "https://login.microsoftonline.com/" + TENANT_A + "/v2.0/extra")) {
            String token = LlavesDePrueba.tokenConEmisorYTid(emisor, TENANT_A, "sub");

            assertThatThrownBy(() -> multiTenant().identidadDe("Bearer " + token))
                    .as(emisor)
                    .isInstanceOf(TokenInvalidoException.class)
                    .hasMessageContaining("emisor");
        }
    }

    // /common y /organizations no son tenants: el token de verdad siempre trae el GUID del directorio
    @Test
    void unEmisorQueNoTraeUnGuidDeTenantNoPasa() {
        for (String ruta : List.of("common", "organizations", "consumers", "mi-empresa")) {
            String emisor = "https://login.microsoftonline.com/" + ruta + "/v2.0";
            String token = LlavesDePrueba.tokenConEmisorYTid(emisor, ruta, "sub");

            assertThatThrownBy(() -> multiTenant().identidadDe("Bearer " + token))
                    .as(emisor)
                    .isInstanceOf(TokenInvalidoException.class)
                    .hasMessageContaining("emisor");
        }
    }

    // ---- la lista de tenants permitidos ----

    @Test
    void conListaDeTenantsSoloEntranLosDeLaLista() {
        OidcTokenVerificador soloA = multiTenant(TENANT_A);

        // el de la lista entra
        assertThat(soloA.identidadDe("Bearer " + LlavesDePrueba.tokenDeTenant(TENANT_A, "a", null, null)).sub())
                .isEqualTo("a");

        // el que no esta en la lista no, aunque su token sea perfecto
        assertThatThrownBy(() -> multiTenant(TENANT_A)
                .identidadDe("Bearer " + LlavesDePrueba.tokenDeTenant(TENANT_B, "b", null, null)))
                .isInstanceOf(TokenInvalidoException.class)
                .hasMessageContaining("tenant");
    }

    @Test
    void laListaDeTenantsNoDistingueMayusculas() {
        OidcTokenVerificador conMayusculas = new OidcTokenVerificador(
                ValidadorDeEmisor.multiTenant(List.of(TENANT_A.toUpperCase())),
                Set.of(LlavesDePrueba.AUDIENCE), new JwksKeyLocator(LlavesDePrueba.jwksUri()));

        assertThat(conMayusculas
                .identidadDe("Bearer " + LlavesDePrueba.tokenDeTenant(TENANT_A, "a", null, null)).sub())
                .isEqualTo("a");
    }

    @Test
    void sinListaDeTenantsEntraCualquieraQueCumplaElPatron() {
        OidcTokenVerificador cualquiera = multiTenant();
        String tenantNuevo = UUID.randomUUID().toString();

        assertThat(cualquiera
                .identidadDe("Bearer " + LlavesDePrueba.tokenDeTenant(tenantNuevo, "nuevo", null, null)).sub())
                .isEqualTo("nuevo");
    }

    // ---- lo que no cambia en multi-tenant ----

    // el resto de la verificacion es la misma: aqui solo se afloja QUIEN puede firmar, nada mas
    @Test
    void elRestoDeLaVerificacionSigueIgualDeEstricta() {
        // el sub sigue siendo obligatorio: sin el no se sabe de quien es el token
        assertThatThrownBy(() -> multiTenant().identidadDe("Bearer "
                + LlavesDePrueba.tokenConEmisorYTid(LlavesDePrueba.emisorDeTenant(TENANT_A), TENANT_A, null)))
                .isInstanceOf(TokenInvalidoException.class)
                .hasMessageContaining("sub");

        // y un token vencido tampoco entra
        assertThatThrownBy(() -> multiTenant().identidadDe("Bearer " + LlavesDePrueba.tokenVencido("sub")))
                .isInstanceOf(TokenInvalidoException.class)
                .hasMessageContaining("vencido");

        // la confusion de algoritmo sigue cerrada
        assertThatThrownBy(() -> multiTenant()
                .identidadDe("Bearer " + LlavesDePrueba.tokenConHs256FirmadoConLaLlavePublica("sub")))
                .isInstanceOf(TokenInvalidoException.class)
                .hasMessageContaining("RS256");

        // y la audiencia tambien: un token de un tenant valido, para otra aplicacion, no entra
        String paraOtraApp = LlavesDePrueba.tokenDeTenantParaLaAudiencia(TENANT_A, "api://otra-app", "sub");
        assertThatThrownBy(() -> multiTenant().identidadDe("Bearer " + paraOtraApp))
                .isInstanceOf(TokenInvalidoException.class)
                .hasMessageContaining("audiencia");
    }

    // ---- con multi-tenant apagado nada de esto aplica ----

    // Lo importante de este caso: con auth.multi-tenant=false el servicio se comporta EXACTAMENTE
    // como antes, un solo emisor comparado tal cual. Un token de un tenant cualquiera de Entra, por
    // perfecto que sea, no entra.
    @Test
    void enModoDeUnSoloTenantElEmisorSigueSiendoFijo() {
        OidcTokenVerificador fijo = new OidcTokenVerificador(LlavesDePrueba.issuer(),
                LlavesDePrueba.AUDIENCE, new JwksKeyLocator(LlavesDePrueba.jwksUri()));

        // el emisor configurado entra, igual que siempre
        assertThat(fijo.identidadDe("Bearer " + LlavesDePrueba.tokenValido("sub", null, null)).sub())
                .isEqualTo("sub");

        // y un emisor de Entra, que en multi-tenant seria valido, aqui no
        assertThatThrownBy(() -> fijo
                .identidadDe("Bearer " + LlavesDePrueba.tokenDeTenant(TENANT_A, "a", null, null)))
                .isInstanceOf(TokenInvalidoException.class)
                .hasMessageContaining("emisor");
    }

    // ---- la audiencia como lista ----

    @Test
    void laAudienciaPuedeSerUnaListaYPasaSiCoincideAlguna() {
        // segun como quede registrada la app, Entra pone api://<client-id> o el <client-id> pelado
        String conPrefijo = "api://growlink-usuarios";
        String clientIdPelado = "7f3c1b2a-0000-0000-0000-abcdefabcdef";
        OidcTokenVerificador lasDos = new OidcTokenVerificador(
                ValidadorDeEmisor.multiTenant(List.of()),
                new LinkedHashSet<>(List.of(conPrefijo, clientIdPelado)),
                new JwksKeyLocator(LlavesDePrueba.jwksUri()));

        // coincide la una o coincide la otra: las dos sirven
        for (String audiencia : List.of(conPrefijo, clientIdPelado)) {
            String token = LlavesDePrueba.tokenDeTenantParaLaAudiencia(TENANT_A, audiencia, "sub");
            assertThat(lasDos.identidadDe("Bearer " + token).sub()).as(audiencia).isEqualTo("sub");
        }

        // y una que no esta en la lista sigue sin entrar
        String deOtraApp = LlavesDePrueba.tokenDeTenantParaLaAudiencia(TENANT_A, "api://otra-app", "sub");
        assertThatThrownBy(() -> lasDos.identidadDe("Bearer " + deOtraApp))
                .isInstanceOf(TokenInvalidoException.class)
                .hasMessageContaining("audiencia");
    }
}
