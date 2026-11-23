package com.saroga.timewarp.infrastructure.security;

import java.util.Arrays;

/** Origenes web permitidos (CORS y WebSocket). Un cliente Unity de escritorio NO envia cabecera Origin, asi que no le afecta. */
public final class AllowedOrigins {
    private AllowedOrigins() {}

    public static String[] from(String raw) {
        if (raw == null || raw.isBlank()) return new String[]{"http://localhost:*", "http://127.0.0.1:*"};
        return Arrays.stream(raw.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toArray(String[]::new);
    }
}
