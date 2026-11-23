package com.saroga.timewarp.infrastructure.security;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/** Anti fuerza bruta: tras MAX_FAILURES intentos fallidos seguidos con la misma clave (IP + usuario) se bloquea LOCK_MILLIS. */
public class LoginAttemptLimiter {

    public static final int MAX_FAILURES = 5;
    public static final long LOCK_MILLIS = 5 * 60_000L;

    private static final class State { int failures; long lockedUntil; long last; }

    private final Map<String, State> states = new ConcurrentHashMap<>();
    private final LongSupplier nowMillis;

    public LoginAttemptLimiter() { this(System::currentTimeMillis); }
    public LoginAttemptLimiter(LongSupplier nowMillis) { this.nowMillis = nowMillis; }

    public boolean isLocked(String key) {
        State s = states.get(key);
        if (s == null) return false;
        synchronized (s) { return s.lockedUntil > nowMillis.getAsLong(); }
    }

    public void failure(String key) {
        long now = nowMillis.getAsLong();
        if (states.size() > 10_000) states.entrySet().removeIf(e -> now - e.getValue().last > LOCK_MILLIS * 2);
        State s = states.computeIfAbsent(key, k -> new State());
        synchronized (s) {
            if (s.lockedUntil != 0 && s.lockedUntil <= now) { s.failures = 0; s.lockedUntil = 0; }   // el bloqueo ya expiro
            s.failures++;
            s.last = now;
            if (s.failures >= MAX_FAILURES) s.lockedUntil = now + LOCK_MILLIS;
        }
    }

    public void success(String key) { states.remove(key); }
}
