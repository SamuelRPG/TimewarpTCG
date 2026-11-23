package com.saroga.timewarp.infrastructure;

import com.saroga.timewarp.infrastructure.security.*;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/** Tokens, limitadores y reglas del interceptor del WebSocket. Sin Spring ni base de datos. */
public class SecurityUnitTest {

    static final String SECRET = "una-clave-de-pruebas-de-mas-de-32-caracteres!!";
    final Clock now = Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC);
    final JwtService jwt = new JwtService(SECRET, Duration.ofMinutes(60), now);
    final AuthPrincipal alice = new AuthPrincipal(UUID.randomUUID(), "alice", false);

    // ------------------------------------------------------------ JWT
    @Test
    void tokenRoundTrip() {
        AuthPrincipal p = jwt.parse(jwt.issue(alice));
        assertEquals(alice.userId(), p.userId());
        assertEquals("alice", p.username());
        assertFalse(p.guest());
        assertTrue(jwt.parse(jwt.issue(new AuthPrincipal(UUID.randomUUID(), "Invitado-1", true))).guest());
    }

    @Test
    void rejectsTamperedExpiredForeignAndUnsignedTokens() {
        String token = jwt.issue(alice);
        // firma alterada
        String[] parts = token.split("\\.");
        String tampered = parts[0] + "." + parts[1] + "." + (parts[2].startsWith("A") ? "B" : "A") + parts[2].substring(1);
        assertThrows(InvalidTokenException.class, () -> jwt.parse(tampered));
        // payload cambiado (otro usuario) con la firma vieja
        String evil = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"iss\":\"timewarp\",\"sub\":\"" + UUID.randomUUID() + "\",\"name\":\"admin\",\"guest\":false,\"exp\":" + (now.instant().getEpochSecond() + 999) + "}").getBytes(StandardCharsets.UTF_8));
        assertThrows(InvalidTokenException.class, () -> jwt.parse(parts[0] + "." + evil + "." + parts[2]));
        // firmado con OTRA clave
        JwtService other = new JwtService("otra-clave-distinta-de-mas-de-32-caracteres!!!", Duration.ofMinutes(60), now);
        assertThrows(InvalidTokenException.class, () -> jwt.parse(other.issue(alice)));
        // caducado
        Clock later = Clock.fixed(now.instant().plus(Duration.ofMinutes(61)), ZoneOffset.UTC);
        assertThrows(InvalidTokenException.class, () -> new JwtService(SECRET, Duration.ofMinutes(60), later).parse(token));
        // sin firma ("alg":"none")
        String none = Jwts.builder().issuer("timewarp").subject(alice.userId().toString()).expiration(Date.from(now.instant().plusSeconds(600))).compact();
        assertThrows(InvalidTokenException.class, () -> jwt.parse(none));
        // basura y vacios
        for (String bad : new String[]{null, "", "   ", "abc", "a.b.c", "Bearer x"})
            assertThrows(InvalidTokenException.class, () -> jwt.parse(bad), "debe rechazar: " + bad);
        // mismo secreto pero emisor distinto
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String otherIssuer = Jwts.builder().issuer("otro").subject(alice.userId().toString()).expiration(Date.from(now.instant().plusSeconds(600))).signWith(key).compact();
        assertThrows(InvalidTokenException.class, () -> jwt.parse(otherIssuer));
    }

    @Test
    void shortSecretIsRejectedAtStartup() {
        assertThrows(IllegalStateException.class, () -> new JwtService("corta", Duration.ofMinutes(1), now));
        assertDoesNotThrow(() -> new JwtService("", Duration.ofMinutes(1), now));   // vacia = clave aleatoria de arranque
    }

    @Test
    void bearerHeaderParsing() {
        assertEquals("abc", JwtService.bearer("Bearer abc"));
        assertEquals("abc", JwtService.bearer("bearer   abc "));
        assertNull(JwtService.bearer("Basic abc"));
        assertNull(JwtService.bearer(null));
    }

    // ------------------------------------------------------------ limitadores
    @Test
    void loginIsLockedAfterFiveFailuresAndUnlocksLater() {
        AtomicLong t = new AtomicLong(1_000);
        LoginAttemptLimiter l = new LoginAttemptLimiter(t::get);
        for (int i = 0; i < 4; i++) { l.failure("ip|bob"); assertFalse(l.isLocked("ip|bob")); }
        l.failure("ip|bob");
        assertTrue(l.isLocked("ip|bob"), "5 fallos => bloqueado");
        assertFalse(l.isLocked("ip|otro"), "otra clave no se ve afectada");
        t.addAndGet(LoginAttemptLimiter.LOCK_MILLIS + 1);
        assertFalse(l.isLocked("ip|bob"), "pasado el bloqueo se desbloquea");
        l.failure("ip|bob");
        assertFalse(l.isLocked("ip|bob"), "tras desbloquear se empieza de cero");
    }

    @Test
    void successResetsFailures() {
        LoginAttemptLimiter l = new LoginAttemptLimiter(() -> 1000L);
        for (int i = 0; i < 4; i++) l.failure("k");
        l.success("k");
        for (int i = 0; i < 4; i++) l.failure("k");
        assertFalse(l.isLocked("k"));
    }

    @Test
    void rateLimiterAllowsBurstThenRefills() {
        AtomicLong t = new AtomicLong(0);
        RateLimiter r = new RateLimiter(3, 1.0, t::get);
        assertTrue(r.tryAcquire("a")); assertTrue(r.tryAcquire("a")); assertTrue(r.tryAcquire("a"));
        assertFalse(r.tryAcquire("a"), "agotada la rafaga");
        assertTrue(r.tryAcquire("b"), "cada clave tiene su cubo");
        t.addAndGet(1000);
        assertTrue(r.tryAcquire("a"), "1 ficha por segundo");
        assertFalse(r.tryAcquire("a"));
    }

    // ------------------------------------------------------------ interceptor STOMP
    private Message<byte[]> frame(StompCommand cmd, String destination, String authHeader, java.security.Principal user) {
        StompHeaderAccessor acc = StompHeaderAccessor.create(cmd);
        acc.setSessionId("session-1");
        if (destination != null) acc.setDestination(destination);
        if (authHeader != null) acc.addNativeHeader("Authorization", authHeader);
        if (user != null) acc.setUser(user);
        acc.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], acc.getMessageHeaders());
    }

    @Test
    void stompConnectRequiresValidToken() {
        StompAuthInterceptor i = new StompAuthInterceptor(jwt, new RateLimiter(100, 100));
        assertThrows(MessagingException.class, () -> i.preSend(frame(StompCommand.CONNECT, null, null, null), null));
        assertThrows(MessagingException.class, () -> i.preSend(frame(StompCommand.CONNECT, null, "Bearer basura", null), null));
        Message<byte[]> ok = frame(StompCommand.CONNECT, null, "Bearer " + jwt.issue(alice), null);
        assertSame(ok, i.preSend(ok, null));
        assertEquals(alice.userId().toString(), StompHeaderAccessor.wrap(ok).getUser().getName(), "el usuario queda asociado a la sesion");
    }

    @Test
    void stompSubscribeOnlyToOwnChannel() {
        StompAuthInterceptor i = new StompAuthInterceptor(jwt, new RateLimiter(100, 100));
        String me = alice.userId().toString();
        assertDoesNotThrow(() -> i.preSend(frame(StompCommand.SUBSCRIBE, "/topic/player/" + me, null, alice), null));
        assertDoesNotThrow(() -> i.preSend(frame(StompCommand.SUBSCRIBE, "/topic/player/" + me + "/errors", null, alice), null));
        String other = UUID.randomUUID().toString();
        assertThrows(MessagingException.class, () -> i.preSend(frame(StompCommand.SUBSCRIBE, "/topic/player/" + other, null, alice), null), "espiar a otro jugador");
        assertThrows(MessagingException.class, () -> i.preSend(frame(StompCommand.SUBSCRIBE, "/topic/player/" + other + "/errors", null, alice), null));
        assertThrows(MessagingException.class, () -> i.preSend(frame(StompCommand.SUBSCRIBE, "/topic/**", null, alice), null));
        assertThrows(MessagingException.class, () -> i.preSend(frame(StompCommand.SUBSCRIBE, "/topic/player/" + me, null, null), null), "sin autenticar");
    }

    @Test
    void stompSendIsRestrictedAndRateLimited() {
        StompAuthInterceptor i = new StompAuthInterceptor(jwt, new RateLimiter(3, 0.0001));
        assertThrows(MessagingException.class, () -> i.preSend(frame(StompCommand.SEND, "/app/game/passPriority", null, null), null), "sin autenticar");
        assertThrows(MessagingException.class, () -> i.preSend(frame(StompCommand.SEND, "/topic/player/x", null, alice), null), "destino no permitido");
        for (int n = 0; n < 3; n++) assertDoesNotThrow(() -> i.preSend(frame(StompCommand.SEND, "/app/game/passPriority", null, alice), null));
        assertThrows(MessagingException.class, () -> i.preSend(frame(StompCommand.SEND, "/app/game/passPriority", null, alice), null), "demasiados mensajes");
    }
}
