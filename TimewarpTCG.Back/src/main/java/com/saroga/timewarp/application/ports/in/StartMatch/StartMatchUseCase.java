package com.saroga.timewarp.application.ports.in.StartMatch;

import com.saroga.timewarp.domain.model.entity.gameState.GameState;
import java.util.UUID;

public interface StartMatchUseCase {

    /**
     * Inicia una partida local usando un mazo preconstruido.
     */
    default GameState executeLocalMatch(UUID playerId, String chosenDeckName) {
        return executeLocalMatch(playerId, chosenDeckName, "RANDOM");
    }

    /** botDeckName: "RANDOM" (mazo aleatorio legal del catalogo), "AGGRO" o "CONTROL". */
    GameState executeLocalMatch(UUID playerId, String chosenDeckName, String botDeckName);

}
