package com.growlink.config;

import com.growlink.LlavesDePrueba;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// El interruptor auth.mode y lo que se exige al arrancar en modo oidc. Lo que importa aqui es que
// una configuracion mal puesta tumbe el arranque en vez de quedarse callada: si el servicio arranca
// igual, se despliega creyendo que la seguridad esta puesta y nadie se entera hasta que alguien
// mira los logs (o hasta que entra quien no debia).
class ModoDeAutenticacionTest {

    private static final String TENANT = "11111111-2222-3333-4444-555555555555";

    @Test
    void losDosModosValidosArrancan() {
        assertThatCode(() -> new AuthModeConfig("legacy")).doesNotThrowAnyException();
        assertThatCode(() -> new AuthModeConfig("oidc")).doesNotThrowAnyException();
    }

    @Test
    void unValorQueNoEsNingunoDeLosDosTumbaElArranque() {
        for (String malo : new String[]{"OIDC", "oidc ", "oauth", "", "si"}) {
            assertThatThrownBy(() -> new AuthModeConfig(malo))
                    .as("auth.mode=" + malo)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("auth.mode");
        }
    }

    // ---- lo que se exige en modo oidc ----

    @Test
    void sinAudienciaNoArrancaEnNingunoDeLosDosModos() {
        assertThatThrownBy(() -> verificador(LlavesDePrueba.issuer(), "", "", false, ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AUTH_AUDIENCE");
        assertThatThrownBy(() -> verificador("", "", "", true, ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AUTH_AUDIENCE");
    }

    // con un solo directorio el emisor es obligatorio: sin el, cualquiera con una llave privada
    // propia puede firmarse un token
    @Test
    void conUnSoloTenantElEmisorEsObligatorio() {
        assertThatThrownBy(() -> verificador("", LlavesDePrueba.AUDIENCE, "", false, ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AUTH_ISSUER_URI");
    }

    // con multi-tenant el emisor viene en el token, asi que AUTH_ISSUER_URI ya no hace falta
    @Test
    void conMultiTenantElEmisorFijoYaNoHaceFalta() {
        assertThatCode(() -> verificador("", LlavesDePrueba.AUDIENCE, "", true, ""))
                .doesNotThrowAnyException();
        // y si viene puesto no estorba (solo se avisa en el log)
        assertThatCode(() -> verificador(LlavesDePrueba.issuer(), LlavesDePrueba.AUDIENCE, "", true, ""))
                .doesNotThrowAnyException();
    }

    // un GUID mal escrito dejaria fuera a una organizacion entera sin que nadie se enterara hasta
    // que alguien de ahi no pudiera entrar
    @Test
    void unTenantMalEscritoEnLaListaTumbaElArranque() {
        for (String malo : new String[]{"no-es-un-guid", "11111111-2222-3333-4444", TENANT + "-extra"}) {
            assertThatThrownBy(() -> verificador("", LlavesDePrueba.AUDIENCE, "", true, TENANT + "," + malo))
                    .as("AUTH_ALLOWED_TENANTS con " + malo)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("AUTH_ALLOWED_TENANTS");
        }
    }

    @Test
    void unaListaDeTenantsBienEscritaArranca() {
        assertThatCode(() -> verificador("", LlavesDePrueba.AUDIENCE, "", true,
                TENANT + ", AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE"))
                .doesNotThrowAnyException();
    }

    private static Object verificador(String issuerUri, String audience, String jwksUri,
                                      boolean multiTenant, String tenantsPermitidos) {
        return new AuthModeConfig("oidc")
                .oidcTokenVerificador(issuerUri, audience, jwksUri, multiTenant, tenantsPermitidos);
    }
}
