package com.saroga.timewarp.infrastructure.security;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;

import java.security.Principal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Seguridad del WebSocket STOMP (canal de entrada):
 *  - CONNECT: obliga a llevar "Authorization: Bearer <token>" valido; el usuario queda asociado a la sesion.
 *  - SUBSCRIBE: solo se permite /topic/player/{TU id}[/errors]  (nadie puede espiar la partida de otro).
 *  - SEND: solo hacia /app/game/** y con limite de mensajes por segundo.
 */
public class StompAuthInterceptor implements ChannelInterceptor {

    private static final Pattern PLAYER_TOPIC = Pattern.compile("^/topic/player/([0-9a-fA-F\\-]{36})(/errors|/status)?$");

    private final JwtService jwt;
    private final RateLimiter limiter;

    public StompAuthInterceptor(JwtService jwt, RateLimiter limiter) {
        this.jwt = jwt;
        this.limiter = limiter;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor acc = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (acc == null || acc.getCommand() == null) return message;

        StompCommand cmd = acc.getCommand();
        if (cmd == StompCommand.CONNECT || cmd == StompCommand.STOMP) {
            try {
                String token = JwtService.bearer(acc.getFirstNativeHeader("Authorization"));
                acc.setUser(jwt.parse(token));
            } catch (InvalidTokenException e) {
                throw new MessagingException("No autenticado: " + e.getMessage());
            }
        } else if (cmd == StompCommand.SUBSCRIBE) {
            Principal user = requireUser(acc);
            String dest = acc.getDestination();
            Matcher m = dest == null ? null : PLAYER_TOPIC.matcher(dest);
            if (m == null || !m.matches() || !m.group(1).equalsIgnoreCase(user.getName())) {
                throw new MessagingException("Suscripcion no permitida: solo puedes escuchar tu propio canal.");
            }
        } else if (cmd == StompCommand.SEND) {
            requireUser(acc);
            String dest = acc.getDestination();
            if (dest == null || !dest.startsWith("/app/game/")) throw new MessagingException("Destino no permitido.");
            if (!limiter.tryAcquire("ws:" + acc.getSessionId())) throw new MessagingException("Demasiados mensajes: ve mas despacio.");
        }
        return message;
    }

    private static Principal requireUser(StompHeaderAccessor acc) {
        Principal user = acc.getUser();
        if (user == null) throw new MessagingException("No autenticado.");
        return user;
    }
}
