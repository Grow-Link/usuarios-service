package com.growlink.adapter.security;

import io.jsonwebtoken.Claims;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

// Quien puede haber firmado el token. Es la parte de la verificacion que cambia segun como este
// registrada la aplicacion en Entra ID, y por eso esta aparte de OidcTokenVerificador: el resto de
// la verificacion (RS256, kid, exp, aud, sub) es igual en los dos casos.
//
//   fijo (auth.multi-tenant=false, por defecto)
//       Un solo emisor, el de AUTH_ISSUER_URI, comparado tal cual. Es lo que habia y lo que sigue
//       habiendo: si la app es de un solo directorio, cualquier otro emisor es un token ajeno.
//
//   multi-tenant (auth.multi-tenant=true)
//       La app esta registrada para cuentas de cualquier directorio organizacional y cuentas
//       personales de Microsoft, asi que el iss trae el tenant de QUIEN inicia sesion y no se puede
//       comparar contra un valor fijo: habria que rechazar a casi todo el mundo.
//
// Lo que NO se hace en multi-tenant es aflojar la comprobacion y dar por bueno cualquier iss. Se
// exigen tres cosas:
//
//   1. que el iss sea exactamente un emisor v2.0 de Entra ID, con el patron completo y anclado (ver
//      EMISOR_DE_ENTRA). Esto es lo que rechaza los parecidos, que es el ataque de este modo: un
//      http:// en vez de https://, o un host como login.microsoftonline.com.evil.com, que a la
//      vista pasa por el bueno y con una comparacion floja ("empieza por", "contiene") entraria.
//   2. que el claim tid sea el MISMO GUID que aparece en el iss. Sin esto, el tenant del que habla
//      el token y el tenant que firma podrian no ser el mismo, y el tid es el dato con el que una
//      aplicacion decide de que organizacion es la persona.
//   3. que ese tenant este en AUTH_ALLOWED_TENANTS, si la lista trae algo. Vacia = cualquier tenant
//      que cumpla 1 y 2, que es lo que quiere decir multi-tenant.
//
// Las cuentas personales de Microsoft entran por el mismo camino: su emisor es el del tenant de MSA
// (9188040d-6c67-4c5b-b112-36a304b66dad), que es un GUID como cualquier otro y cumple el patron.
public final class ValidadorDeEmisor {

    // el GUID del tenant, suelto para no repetirlo: Entra lo publica indistintamente en minusculas
    // o mayusculas, por eso acepta las dos
    private static final String GUID = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

    // El emisor v2.0 de Entra ID, anclado de punta a punta y sin nada opcional. Se usa con
    // matches(), que exige que el iss sea TODO el patron: asi no pasa ni un sufijo pegado ni un
    // salto de linea al final.
    static final Pattern EMISOR_DE_ENTRA =
            Pattern.compile("^https://login\\.microsoftonline\\.com/(" + GUID + ")/v2\\.0$");

    private static final Pattern SOLO_GUID = Pattern.compile("^" + GUID + "$");

    private static final String CLAIM_TENANT = "tid";

    // en multi-tenant queda null: no hay un emisor contra el que comparar
    private final String emisorFijo;

    // vacio = cualquier tenant que cumpla el patron. Siempre en minusculas, para comparar sin
    // distinguir mayusculas
    private final Set<String> tenantsPermitidos;

    private ValidadorDeEmisor(String emisorFijo, Set<String> tenantsPermitidos) {
        this.emisorFijo = emisorFijo;
        this.tenantsPermitidos = tenantsPermitidos;
    }

    /** Un solo emisor, comparado tal cual: el comportamiento de siempre. */
    public static ValidadorDeEmisor fijo(String emisor) {
        return new ValidadorDeEmisor(emisor, Set.of());
    }

    /**
     * Cualquier tenant de Entra ID que cumpla el patron del emisor v2.0 y cuyo tid cuadre con el
     * del iss. Si {@code tenantsPermitidos} trae algo, ademas tiene que estar en la lista.
     */
    public static ValidadorDeEmisor multiTenant(Collection<String> tenantsPermitidos) {
        return new ValidadorDeEmisor(null, tenantsPermitidos.stream()
                .map(tenant -> tenant.trim().toLowerCase(Locale.ROOT))
                .filter(tenant -> !tenant.isEmpty())
                .collect(Collectors.toUnmodifiableSet()));
    }

    /** Para validar AUTH_ALLOWED_TENANTS al arrancar: un GUID mal escrito no debe pasar callado. */
    public static boolean esGuid(String valor) {
        return valor != null && SOLO_GUID.matcher(valor.trim()).matches();
    }

    public boolean esMultiTenant() {
        return emisorFijo == null;
    }

    public Set<String> getTenantsPermitidos() {
        return tenantsPermitidos;
    }

    /** Cualquier problema sale como TokenInvalidoException, con el motivo para el log del servidor. */
    public void exigir(Claims claims) {
        String emisor = claims.getIssuer();
        if (emisorFijo != null) {
            exigirEmisorFijo(emisor);
            return;
        }
        exigirEmisorDeEntra(emisor, claims);
    }

    private void exigirEmisorFijo(String emisor) {
        if (emisorFijo.isBlank()) {
            return; // sin emisor configurado no hay nada que comparar (AuthModeConfig lo exige al arrancar)
        }
        if (!emisorFijo.equals(emisor)) {
            throw new TokenInvalidoException("el emisor del token (" + emisor + ") no es " + emisorFijo);
        }
    }

    private void exigirEmisorDeEntra(String emisor, Claims claims) {
        if (emisor == null || emisor.isBlank()) {
            throw new TokenInvalidoException("el token no trae iss, no se sabe que emisor lo firmo");
        }
        Matcher coincidencia = EMISOR_DE_ENTRA.matcher(emisor);
        if (!coincidencia.matches()) {
            throw new TokenInvalidoException(
                    "el emisor del token (" + emisor + ") no es un emisor v2.0 de Entra ID");
        }
        String tenantDelEmisor = coincidencia.group(1);

        // El tid tiene que ser el mismo tenant que firma. Se compara sin distinguir mayusculas
        // porque el GUID puede venir en cualquier caja, pero tiene que venir: un token sin tid en
        // modo multi-tenant no dice de que organizacion es la persona.
        Object tid = claims.get(CLAIM_TENANT);
        String tenantDelClaim = tid == null ? null : tid.toString().trim();
        if (tenantDelClaim == null || tenantDelClaim.isEmpty()) {
            throw new TokenInvalidoException("el token no trae tid, y con multi-tenant es obligatorio");
        }
        if (!tenantDelClaim.equalsIgnoreCase(tenantDelEmisor)) {
            throw new TokenInvalidoException("el tid del token (" + tenantDelClaim
                    + ") no es el tenant de su emisor (" + tenantDelEmisor + ")");
        }

        if (!tenantsPermitidos.isEmpty()
                && !tenantsPermitidos.contains(tenantDelEmisor.toLowerCase(Locale.ROOT))) {
            throw new TokenInvalidoException(
                    "el tenant " + tenantDelEmisor + " no esta en la lista de tenants permitidos");
        }
    }
}
