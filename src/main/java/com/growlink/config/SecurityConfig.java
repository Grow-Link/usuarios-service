package com.growlink.config;

import com.growlink.adapter.security.OidcAuthenticationFilter;
import com.growlink.adapter.security.TokenAuthenticationFilter;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.Optional;

@Configuration
public class SecurityConfig {

    // Quien pone la identidad depende de auth.mode (ver AuthModeConfig):
    //   legacy -> TokenAuthenticationFilter, con los tokens HS256 que firma este servicio
    //   oidc   -> OidcAuthenticationFilter, con los tokens de Microsoft
    // Los dos dejan lo mismo en el contexto (el id del usuario como nombre y ROLE_<rol> como
    // permiso), asi que de aqui para abajo nada mas cambia.
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, TokenAuthenticationFilter tokenFilter,
                                           Optional<OidcAuthenticationFilter> oidcFilter,
                                           MetricasDeSeguridad metricas) throws Exception {
        Filter puerta = oidcFilter.map(filtro -> (Filter) filtro).orElse(tokenFilter);
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/ws/**").permitAll()
                        // se autentica con la llave interna dentro del controlador
                        .requestMatchers("/api/interno/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        // el balanceador y Azure preguntan por la salud sin credenciales (solo health, sin detalles)
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                // sin esto, pedir algo sin token da 403 en vez de 401
                // ya nos paso una vez asi que lo dejamos explicito
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            // el 401 que no pasa por el filtro (no llego token, o el del modo legacy
                            // no sirve): queda contado y con una linea de log, igual que los demas
                            metricas.tokenInvalido(request.getMethod(), request.getRequestURI(),
                                    "la peticion no trae una autenticacion valida");
                            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                        })
                        // un 403 de las reglas de autorizacion tambien es un evento de seguridad
                        .accessDeniedHandler((request, response, denegada) -> {
                            metricas.prohibido(request.getMethod(), request.getRequestURI(),
                                    denegada.getMessage());
                            response.sendError(HttpServletResponse.SC_FORBIDDEN);
                        }))
                .addFilterBefore(puerta, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // TokenAuthenticationFilter es un @Component, y Spring Boot registra solo en la cadena del
    // contenedor cualquier bean que sea un Filter. Ahi no sirve de nada (corre despues de que Spring
    // Security ya decidio) y en modo oidc seria peor: dejaria entrar la identidad de un token HS256
    // del modo viejo despues de las reglas de autorizacion. Con la registracion apagada, el filtro
    // solo corre donde tiene que correr: dentro de la cadena de Spring Security.
    @Bean
    public FilterRegistrationBean<TokenAuthenticationFilter> noRegistrarTokenFilterDosVeces(
            TokenAuthenticationFilter tokenFilter) {
        FilterRegistrationBean<TokenAuthenticationFilter> registro = new FilterRegistrationBean<>(tokenFilter);
        registro.setEnabled(false);
        return registro;
    }
}
