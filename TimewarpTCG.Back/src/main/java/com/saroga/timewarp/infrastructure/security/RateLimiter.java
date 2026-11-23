package com.saroga.timewarp.infrastructure.security;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/** Cubo de fichas por clave (IP, sesion...): se permite una rafaga de 'capacity' y luego 'refillPerSecond' por segundo. */
public class RateLimiter {

    private static final class Bucket { double tokens; long last; }

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final double capacity, refillPerSecond;
    private final LongSupplier nowMillis;

    public RateLimiter(double capacity, double refillPerSecond) { this(capacity, refillPerSecond, System::currentTimeMillis); }

    public RateLimiter(double capacity, double refillPerSecond, LongSupplier nowMillis) {
        this.capacity = capacity; this.refillPerSecond = refillPerSecond; this.nowMillis = nowMillis;
    }

    public boolean tryAcquire(String key) {
        long now = nowMillis.getAsLong();
        if (buckets.size() > 10_000) buckets.entrySet().removeIf(e -> now - e.getValue().last > 600_000);   // limpieza
        Bucket b = buckets.computeIfAbsent(key, k -> { Bucket nb = new Bucket(); nb.tokens = capacity; nb.last = now; return nb; });
        synchronized (b) {
            b.tokens = Math.min(capacity, b.tokens + (now - b.last) / 1000.0 * refillPerSecond);
            b.last = now;
            if (b.tokens < 1) return false;
            b.tokens -= 1;
            return true;
        }
    }

    public void forget(String key) { buckets.remove(key); }
}
