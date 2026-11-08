package com.growlink.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// El interruptor auth.mode. Lo que importa aqui es el caso del valor mal escrito: si el servicio
// arrancara igual cayendo a legacy, se podria desplegar con el login viejo creyendo que ya esta
// puesto el de Microsoft, y nadie se enteraria hasta que alguien mirara los logs.
class ModoDeAutenticacionTest {

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
}
