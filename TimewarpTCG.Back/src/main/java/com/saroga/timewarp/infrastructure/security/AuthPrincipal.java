package com.saroga.timewarp.infrastructure.security;

import java.security.Principal;
import java.util.UUID;

/**
 * Quien hace la peticion, SACADO DE UN TOKEN FIRMADO por el servidor (nunca del cuerpo del mensaje).
 * getName() es el id del jugador: es el que se usa para sus partidas y para el canal /topic/player/{id}.
 */
public record AuthPrincipal(UUID userId, String username, boolean guest) implements Principal {
    @Override
    public String getName() {
        return userId.toString();
    }
}
