package com.saroga.timewarp.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Emite y valida tokens JWT firmados con HMAC. Solo se aceptan tokens FIRMADOS con nuestra clave
 * (jjwt rechaza "alg":"none" y firmas de otro algoritmo/clave) y con nuestro emisor; los caducados se rechazan.
 */
public class JwtService {

    private static final String ISSUER = "timewarp";
    public static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final Duration ttl;
    private final Clock clock;

    public JwtService(String secret, Duration ttl, Clock clock) {
        this.ttl = ttl;
        this.clock = clock;
        byte[] bytes;
        if (secret == null || secret.isBlank()) {
            bytes = new byte[48];
            new SecureRandom().nextBytes(bytes);
            System.out.println("[SEGURIDAD] TIMEWARP_JWT_SECRET no esta definida: se usa una clave aleatoria de este arranque. "
                    + "Los tokens dejaran de valer al reiniciar. En produccion define TIMEWARP_JWT_SECRET (minimo " + MIN_SECRET_BYTES + " caracteres).");
        } else {
            bytes = secret.getBytes(StandardCharsets.UTF_8);
            if (bytes.length < MIN_SECRET_BYTES) {
                throw new IllegalStateException("timewarp.security.jwt-secret / TIMEWARP_JWT_SECRET debe tener al menos "
                        + MIN_SECRET_BYTES + " caracteres (tiene " + bytes.length + ").");
            }
        }
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    public String issue(AuthPrincipal p) {
        Instant now = clock.instant();
        return Jwts.builder()
                .issuer(ISSUER)
                .subject(p.userId().toString())
                .claim("name", p.username())
                .claim("guest", p.guest())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    public AuthPrincipal parse(String token) {
        if (token == null || token.isBlank()) throw new InvalidTokenException("Falta el token de sesion.");
        try {
            Claims c = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(ISSUER)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token.trim())
                    .getPayload();
            return new AuthPrincipal(UUID.fromString(c.getSubject()), c.get("name", String.class), Boolean.TRUE.equals(c.get("guest", Boolean.class)));
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidTokenException("Sesion no valida o caducada. Vuelve a iniciar sesion.");
        }
    }

    /** Extrae el token de una cabecera "Authorization: Bearer xxx". */
    public static String bearer(String header) {
        if (header == null) return null;
        String h = header.trim();
        return h.regionMatches(true, 0, "Bearer ", 0, 7) ? h.substring(7).trim() : null;
    }
}
