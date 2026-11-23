package com.saroga.timewarp.application.ports.out.GameRepository;

import com.saroga.timewarp.domain.model.entity.gameState.GameState;

import java.util.UUID;

public interface GameRepositoryPort {
    void save (GameState gameState);
    GameState findById(UUID gameId);
    void delete(UUID gameId);
    /** Borra las partidas en las que participa el jugador (solo se permite una partida activa por jugador). */
    void deleteByPlayerId(UUID playerId);

    /** La partida activa del jugador, si tiene alguna. */
    java.util.Optional<GameState> findByPlayerId(UUID playerId);
}
