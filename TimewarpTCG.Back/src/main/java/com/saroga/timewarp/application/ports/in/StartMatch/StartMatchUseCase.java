package com.saroga.timewarp.application.ports.in.StartMatch;

import com.saroga.timewarp.domain.model.entity.gameState.GameState;
import java.util.UUID;

public interface StartMatchUseCase {

    /**
     * Inicia una partida local usando un mazo preconstruido.
     */
    GameState executeLocalMatch(UUID playerId, String chosenDeckName);

}
