package com.growlink.adapter.security;

import com.growlink.domain.Rol;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * HU-01 es "login" sin contraseña, pero HU-02 exige que el backend valide el
 * rol de verdad, no solo esconder botones en el frontend - por eso el token
 * va firmado igual que un JWT real: nadie puede editar su propio rol a mano.
 */
@Service
public class TokenService {

    private final SecretKey key;
    private final long expirationMinutes;

    public TokenService(@Value("${growlink.jwt.secret}") String secret,
                         @Value("${growlink.jwt.expiration-minutes}") long expirationMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMinutes = expirationMinutes;
    }

    public String emitir(Long usuarioId, Rol rol) {
        Instant ahora = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(usuarioId))
                .claim("rol", rol.name())
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plusSeconds(expirationMinutes * 60)))
                .signWith(key)
                .compact();
    }

    public record Sesion(Long usuarioId, Rol rol) {
    }

    public Optional<Sesion> validar(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            Long usuarioId = Long.valueOf(claims.getSubject());
            Rol rol = Rol.valueOf(claims.get("rol", String.class));
            return Optional.of(new Sesion(usuarioId, rol));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
