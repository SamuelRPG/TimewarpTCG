package com.saroga.timewarp.application.service.Matchmaking;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Cola de emparejamiento "partida rapida": el primero que espera se empareja con el siguiente que llega (FIFO).
 * Logica pura (sin Spring ni red) para poder probarla. Es segura entre hilos: todas las operaciones estan sincronizadas.
 */
public class MatchmakingService {

    public record Waiting(UUID playerId, String name, String deckName, long since) { }

    /** first = quien llevaba esperando; second = quien acaba de llegar. */
    public record Pair(Waiting first, Waiting second) { }

    private final Map<UUID, Waiting> queue = new LinkedHashMap<>();
    private final long maxWaitMillis;
    private final LongSupplier clock;

    public MatchmakingService(long maxWaitMillis) { this(maxWaitMillis, System::currentTimeMillis); }

    public MatchmakingService(long maxWaitMillis, LongSupplier clock) {
        this.maxWaitMillis = maxWaitMillis;
        this.clock = clock;
    }

    /** Entra en la cola. Si ya habia alguien esperando, devuelve la pareja (y ambos salen de la cola). */
    public synchronized Optional<Pair> join(UUID playerId, String name, String deckName) {
        queue.remove(playerId);                                  // volver a pulsar "buscar" solo refresca la espera
        Waiting me = new Waiting(playerId, name, deckName, clock.getAsLong());
        for (Waiting other : queue.values()) {
            if (!other.playerId().equals(playerId)) {
                queue.remove(other.playerId());
                return Optional.of(new Pair(other, me));
            }
        }
        queue.put(playerId, me);
        return Optional.empty();
    }

    /** Sale de la cola. Devuelve true si estaba esperando. */
    public synchronized boolean leave(UUID playerId) { return queue.remove(playerId) != null; }

    public synchronized boolean isWaiting(UUID playerId) { return queue.containsKey(playerId); }

    public synchronized int size() { return queue.size(); }

    /** Saca de la cola a quien lleva demasiado esperando y los devuelve para avisarles. */
    public synchronized List<Waiting> expire() {
        long limit = clock.getAsLong() - maxWaitMillis;
        List<Waiting> out = new ArrayList<>();
        queue.values().removeIf(w -> { boolean old = w.since() < limit; if (old) out.add(w); return old; });
        return out;
    }
}
