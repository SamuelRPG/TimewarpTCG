package com.saroga.timewarp.infrastructure.persistence.InMemory;

import com.saroga.timewarp.application.ports.out.GameRepository.GameRepositoryPort;
import com.saroga.timewarp.domain.model.entity.gameState.GameState;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryGameRepository implements GameRepositoryPort {

    private final Map<UUID, GameState> storage = new ConcurrentHashMap<>();

    @Override
    public void save(GameState gameState) {
        if (gameState == null || gameState.getGameId() == null) {
            throw new IllegalStateException("No se encuentra el id de este game state");
        }
        storage.put(gameState.getGameId(), gameState);
    }

    @Override
    public GameState findById(UUID gameId) {
        GameState gameState = storage.get(gameId);
        if (gameState == null) {
            throw new IllegalStateException("No se encuentra el id de este game state");
        }
        return gameState;
    }

    // <-- AÑADIDO: Libera la memoria al instante
    @Override
    public void delete(UUID gameId) {
        storage.remove(gameId);
    }
}