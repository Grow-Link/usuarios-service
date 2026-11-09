package com.growlink.adapter.security;

import com.growlink.LlavesDePrueba;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Los puntos de decision de la verificacion del token de Microsoft, uno por uno y sin levantar el
// contexto de Spring. El par de llaves RSA lo genera la prueba (ver LlavesDePrueba), que tambien
// publica el JWKS en un servidor local, asi que esto recorre el camino de verdad: bajar la llave
// publica del emisor y verificar la firma con ella.
class OidcTokenVerificadorTest {

    private static final String SUB = "00000000-1111-2222-3333-444444444444";

    // uno nuevo por prueba a proposito: JwksKeyLocator cachea las llaves y limita cada cuanto
    // recarga, y con un cache compartido el resultado de una prueba dependeria de en que orden
    // corrieron las demas
    private OidcTokenVerificador verificador() {
        return new OidcTokenVerificador(LlavesDePrueba.issuer(), LlavesDePrueba.AUDIENCE,
                new JwksKeyLocator(LlavesDePrueba.jwksUri()));
    }

    // ---- lo que si pasa ----

    @Test
    void unTokenValidoDevuelveElSubElCorreoYElNombre() {
        var identidad = verificador().identidadDe(
                "Bearer " + LlavesDePrueba.tokenValido(SUB, "ana@growlink.co", "Ana Torres"));

        assertThat(identidad).isEqualTo(
                new OidcTokenVerificador.Identidad(SUB, "ana@growlink.co", "Ana Torres"));
    }

    // Entra ID no siempre publica el claim "email": muchas veces el correo solo viene en
    // preferred_username, y sin leerlo la persona entraria sin correo y nunca podria ser ADMIN
    @Test
    void siNoVieneEmailElCorreoSaleDePreferredUsername() {
        var identidad = verificador().identidadDe(
                "Bearer " + LlavesDePrueba.tokenConPreferredUsername(SUB, "ana@growlink.co", "Ana Torres"));

        assertThat(identidad.correo()).isEqualTo("ana@growlink.co");
    }

    // una cuenta sin correo publicado: el token es valido, simplemente no se sabe su correo
    @Test
    void unTokenSinCorreoSigueSiendoValido() {
        var identidad = verificador().identidadDe("Bearer " + LlavesDePrueba.tokenSinCorreo(SUB, "Ana Torres"));

        assertThat(identidad.sub()).isEqualTo(SUB);
        assertThat(identidad.correo()).isNull();
        assertThat(identidad.nombre()).isEqualTo("Ana Torres");
    }

    // el mismo verificador atiende todas las peticiones: el JWKS se baja una vez y las llaves se
    // reusan del cache, no una llamada HTTP al emisor por cada peticion
    @Test
    void elJwksSeCacheaEntrePeticiones() {
        OidcTokenVerificador unoSolo = verificador();

        for (int i = 0; i < 3; i++) {
            assertThat(unoSolo.identidadDe("Bearer " + LlavesDePrueba.tokenValido(SUB, null, null)).sub())
                    .isEqualTo(SUB);
        }
    }

    // ---- lo que no pasa ----

    @Test
    void sinTokenNoPasa() {
        assertThatThrownBy(() -> verificador().identidadDe(null))
                .isInstanceOf(TokenInvalidoException.class)
                .hasMessageContaining("no trae token");
    }

    @Test
    void unTokenSinElPrefijoBearerNoPasa() {
        assertThatThrownBy(() -> verificador().identidadDe(LlavesDePrueba.tokenValido(SUB, null, null)))
                .isInstanceOf(TokenInvalidoException.class)
                .hasMessageContaining("Bearer");
    }

    @Test
    void unTokenVencidoNoPasa() {
        assertThatThrownBy(() -> verificador().identidadDe("Bearer " + LlavesDePrueba.tokenVencido(SUB)))
                .isInstanceOf(TokenInvalidoException.class)
                .hasMessageContaining("vencido");
    }

    // la firma solo dice "esto lo firmo alguien con esa llave privada", no "esto lo firmo quien yo
    // espero": sin revisar el emisor entraria un token valido de otro tenant o de otro proveedor
    @Test
    void unTokenDeOtroEmisorNoPasa() {
        assertThatThrownBy(() -> verificador().identidadDe("Bearer " + LlavesDePrueba.tokenDeOtroEmisor(SUB)))
                .isInstanceOf(TokenInvalidoException.class)
                .hasMessageContaining("emisor");
    }

    // sin revisar la audiencia, el token que el mismo emisor le dio a la persona para CUALQUIER otra
    // aplicacion del tenant serviria para entrar aqui
    @Test
    void unTokenParaOtraAudienciaNoPasa() {
        assertThatThrownBy(() -> verificador().identidadDe("Bearer " + LlavesDePrueba.tokenParaOtraAudiencia(SUB)))
                .isInstanceOf(TokenInvalidoException.class)
                .hasMessageContaining("audiencia");
    }

    // mismo emisor, misma audiencia, mismo kid, sin vencer... pero firmado con otra llave privada
    @Test
    void unTokenFirmadoPorOtroNoPasa() {
        assertThatThrownBy(() -> verificador().identidadDe("Bearer " + LlavesDePrueba.tokenFirmadoPorUnImpostor(SUB)))
                .isInstanceOf(TokenInvalidoException.class);
    }

    @Test
    void unTokenInventadoNoPasa() {
        assertThatThrownBy(() -> verificador().identidadDe("Bearer esto.no.es-un-jwt"))
                .isInstanceOf(TokenInvalidoException.class);
    }

    @Test
    void unTokenSinSubNoPasa() {
        assertThatThrownBy(() -> verificador().identidadDe("Bearer " + LlavesDePrueba.tokenSinSub()))
                .isInstanceOf(TokenInvalidoException.class)
                .hasMessageContaining("sub");
    }

    @Test
    void siElJwksNoRespondeElTokenNoPasa() {
        OidcTokenVerificador sinEmisor = new OidcTokenVerificador(LlavesDePrueba.issuer(),
                LlavesDePrueba.AUDIENCE, new JwksKeyLocator("http://127.0.0.1:1/.well-known/jwks.json"));

        assertThatThrownBy(() -> sinEmisor.identidadDe("Bearer " + LlavesDePrueba.tokenValido(SUB, null, null)))
                .isInstanceOf(TokenInvalidoException.class);
    }

    // ---- algoritmo: aqui solo se acepta RS256 ----

    // El ataque de quitarle la firma al token: header con alg=none, los claims que el atacante
    // quiera y una tercera parte vacia. Un verificador que acepte JWT sin proteger se lo come
    @Test
    void unTokenConAlgNoneNoPasa() {
        assertThatThrownBy(() -> verificador().identidadDe("Bearer " + LlavesDePrueba.tokenConAlgNone(SUB)))
                .isInstanceOf(TokenInvalidoException.class);
    }

    @Test
    void unTokenFirmadoConHs256ConUnSecretoCualquieraNoPasa() {
        assertThatThrownBy(() -> verificador().identidadDe("Bearer " + LlavesDePrueba.tokenConHs256(SUB)))
                .isInstanceOf(TokenInvalidoException.class)
                .hasMessageContaining("RS256");
    }

    // La confusion de algoritmo: el atacante usa la llave PUBLICA del emisor (que cualquiera baja
    // del JWKS) como secreto de HMAC y se firma un token con el sub que quiera. Si el verificador no
    // fija el algoritmo, valida ese token contra la misma llave y lo da por bueno
    @Test
    void laConfusionDeAlgoritmoConLaLlavePublicaComoSecretoNoPasa() {
        String tokenDelAtacante = LlavesDePrueba.tokenConHs256FirmadoConLaLlavePublica(SUB);

        assertThatThrownBy(() -> verificador().identidadDe("Bearer " + tokenDelAtacante))
                .isInstanceOf(TokenInvalidoException.class)
                .hasMessageContaining("RS256");
    }

    // ---- kid y exp ----

    @Test
    void unTokenSinKidNoPasa() {
        assertThatThrownBy(() -> verificador().identidadDe("Bearer " + LlavesDePrueba.tokenSinKid(SUB)))
                .isInstanceOf(TokenInvalidoException.class)
                .hasMessageContaining("kid");
    }

    @Test
    void unTokenConUnKidQueElEmisorNoPublicaNoPasa() {
        assertThatThrownBy(() -> verificador().identidadDe("Bearer " + LlavesDePrueba.tokenConUnKidQueNoExiste(SUB)))
                .isInstanceOf(TokenInvalidoException.class)
                .hasMessageContaining("kid");
    }

    // jjwt solo compara exp si el claim viene: un token sin exp no venceria nunca, asi que se exige
    @Test
    void unTokenSinExpNoPasa() {
        assertThatThrownBy(() -> verificador().identidadDe("Bearer " + LlavesDePrueba.tokenSinVencimiento(SUB)))
                .isInstanceOf(TokenInvalidoException.class)
                .hasMessageContaining("exp");
    }
}
