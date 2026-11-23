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

    /** Partida entre DOS personas. Cada una usa su mazo (AGGRO / CONTROL o uno guardado en su cuenta). Lanza IllegalArgument/IllegalState si un mazo no es valido. */
    GameState executeVersusMatch(UUID playerAId, String deckAName, UUID playerBId, String deckBName);

    /** Comprueba que el jugador puede jugar con ese mazo (existe y es legal) sin crear partida. Lanza IllegalArgument/IllegalState si no. */
    void checkDeck(UUID playerId, String deckName);

}
