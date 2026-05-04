package com.saroga.timewarp.application.ports.out.GameRepository;

import com.saroga.timewarp.domain.model.entity.gameState.GameState;

import java.util.UUID;

public interface GameRepositoryPort {
    void save (GameState gameState);
    GameState findById(UUID gameId);
}
