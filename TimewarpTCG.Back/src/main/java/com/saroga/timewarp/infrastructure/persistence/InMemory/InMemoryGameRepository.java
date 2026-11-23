package com.saroga.timewarp.infrastructure.persistence.InMemory;

import com.saroga.timewarp.application.ports.out.GameRepository.GameRepositoryPort;
import com.saroga.timewarp.domain.model.entity.gameState.GameState;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Partidas en RAM. Con limites para que no se pueda agotar la memoria del servidor:
 *  - maximo de partidas simultaneas (timewarp.games.max-concurrent)
 *  - las partidas inactivas caducan (timewarp.games.idle-minutes); se purgan cada minuto
 */
@Repository
public class InMemoryGameRepository implements GameRepositoryPort {

    private final Map<UUID, GameState> storage = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastAccess = new ConcurrentHashMap<>();
    private final int maxGames;
    private final long idleMillis;

    public InMemoryGameRepository(@Value("${timewarp.games.max-concurrent:1000}") int maxGames,
                                  @Value("${timewarp.games.idle-minutes:30}") long idleMinutes) {
        this.maxGames = maxGames;
        this.idleMillis = idleMinutes * 60_000L;
    }

    @Override
    public void save(GameState gameState) {
        if (gameState == null || gameState.getGameId() == null) {
            throw new IllegalStateException("No se encuentra el id de este game state");
        }
        if (!storage.containsKey(gameState.getGameId()) && storage.size() >= maxGames) {
            purgeIdle();
            if (storage.size() >= maxGames) throw new IllegalStateException("El servidor esta lleno. Vuelve a intentarlo en unos minutos.");
        }
        storage.put(gameState.getGameId(), gameState);
        lastAccess.put(gameState.getGameId(), System.currentTimeMillis());
    }

    @Override
    public GameState findById(UUID gameId) {
        GameState gameState = gameId == null ? null : storage.get(gameId);
        if (gameState == null) {
            throw new IllegalStateException("No se encuentra el id de este game state");
        }
        lastAccess.put(gameId, System.currentTimeMillis());
        return gameState;
    }

    // <-- AÑADIDO: Libera la memoria al instante
    @Override
    public void delete(UUID gameId) {
        storage.remove(gameId);
        lastAccess.remove(gameId);
    }

    @Override
    public java.util.Optional<GameState> findByPlayerId(UUID playerId) {
        return storage.values().stream()
                .filter(g -> g.getPlayerA().getPlayerId().equals(playerId) || g.getPlayerB().getPlayerId().equals(playerId))
                .findFirst();
    }

    @Override
    public void deleteByPlayerId(UUID playerId) {
        storage.entrySet().removeIf(e -> {
            GameState g = e.getValue();
            boolean mine = g.getPlayerA().getPlayerId().equals(playerId) || g.getPlayerB().getPlayerId().equals(playerId);
            if (mine) lastAccess.remove(e.getKey());
            return mine;
        });
    }

    /** Borra las partidas sin actividad reciente. */
    @Scheduled(fixedDelayString = "${timewarp.games.purge-interval-ms:60000}")
    public void purgeIdle() {
        long limit = System.currentTimeMillis() - idleMillis;
        storage.keySet().removeIf(id -> {
            Long last = lastAccess.get(id);
            boolean idle = last == null || last < limit;
            if (idle) lastAccess.remove(id);
            return idle;
        });
    }

    public int count() { return storage.size(); }
}
