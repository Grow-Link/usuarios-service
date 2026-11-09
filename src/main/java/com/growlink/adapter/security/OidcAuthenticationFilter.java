package com.growlink.adapter.security;

import com.growlink.application.IngresoService;
import com.growlink.config.MetricasDeSeguridad;
import com.growlink.domain.Usuario;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

// La puerta de entrada con auth.mode=oidc. Reemplaza a TokenAuthenticationFilter (ver
// SecurityConfig): en este modo los tokens los emite Microsoft, no este servicio.
//
// Deja EXACTAMENTE lo mismo que el filtro del modo legacy: un Authentication cuyo nombre es el id
// numerico del usuario local y cuyos permisos son ROLE_<rol>. Asi ningun controller se entera de
// con que modo entro la persona (todos hacen Long.valueOf(authentication.getName())) y no hay que
// tocar ni el perfil, ni el jardin, ni el home, ni la busqueda de personas.
//
// Dos casos que se tratan distinto a proposito:
//   sin header Authorization  no se rechaza aqui: se sigue la cadena sin autenticar y deciden las
//                             reglas de SecurityConfig. El 401 lo da su punto de entrada para lo
//                             que pide sesion, y /actuator/health y /api/auth/** siguen abiertos
//                             (si este filtro rechazara, la salud dejaria de responderle al
//                             balanceador en cuanto se prendiera el modo oidc).
//   con un token que no pasa  401 de inmediato, sin tocar la base de datos. Un token presente y
//                             malo es un error del cliente que hay que decirle, no una peticion
//                             anonima: dejarlo seguir como anonimo convertiria un token vencido en
//                             un 403 confuso en vez de un 401 que el frontend sabe atender
//                             (renovar el token y repetir).
public class OidcAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";

    private final OidcTokenVerificador verificador;
    private final IngresoService ingresoService;
    private final MetricasDeSeguridad metricas;

    public OidcAuthenticationFilter(OidcTokenVerificador verificador, IngresoService ingresoService,
                                    MetricasDeSeguridad metricas) {
        this.verificador = verificador;
        this.ingresoService = ingresoService;
        this.metricas = metricas;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HEADER);
        if (header == null || header.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }
        try {
            OidcTokenVerificador.Identidad identidad = verificador.identidadDe(header);
            // primer ingreso: aqui se crea el usuario local con el sub del token
            Usuario usuario = ingresoService.ingresar(identidad);
            var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getRol().name()));
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(String.valueOf(usuario.getId()), null, authorities));
        } catch (TokenInvalidoException e) {
            // el motivo exacto queda en el log del servidor con el X-Request-Id de la peticion;
            // al cliente se le dice lo justo, sin pistas de que parte del token hay que arreglar
            metricas.tokenInvalido(request.getMethod(), request.getRequestURI(), e.getMessage());
            responder401(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    // misma forma que el resto de los errores del servicio (ver GlobalExceptionHandler.ErrorBody)
    private static void responder401(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"message\":\"Token invalido o vencido.\"}");
    }
}
