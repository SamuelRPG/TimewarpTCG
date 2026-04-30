package com.saroga.timewarp.application.service.StartMatch;

import com.saroga.timewarp.application.factory.DeckFactory;
import com.saroga.timewarp.application.ports.in.StartMatch.StartMatchUseCase;
import com.saroga.timewarp.domain.model.entity.deck.Deck;
import com.saroga.timewarp.domain.model.entity.gameState.GameState;
import com.saroga.timewarp.domain.model.entity.playerState.PlayerState;

import java.util.UUID;

public class StartMatchService implements StartMatchUseCase {

    private final DeckFactory deckFactory;

    // Inyección de dependencias: El servicio necesita la fábrica para trabajar
    public StartMatchService(DeckFactory deckFactory) {
        this.deckFactory = deckFactory;
    }

    @Override
    public GameState executeLocalMatch(UUID playerId, String chosenDeckName) {

        // 1. Creamos el mazo del jugador con la fábrica
        Deck playerDeck = deckFactory.getDeckByName(chosenDeckName, playerId);

        // 2. Creamos un "Bot" automático como rival (UUID aleatorio)
        UUID botId = UUID.randomUUID();
        Deck botDeck = deckFactory.getDeckByName("CONTROL", botId);

        // 3. Inicializamos los estados
        PlayerState pA = new PlayerState(playerId, playerDeck.getCards(), true);
        PlayerState pB = new PlayerState(botId, botDeck.getCards(), false);

        // 4. Creamos la partida global
        UUID newGameId = UUID.randomUUID();
        GameState newGameState = new GameState(newGameId, pA, pB);

        // 5. Robamos las 4 cartas iniciales para cada jugador
        for (int i = 0; i < 4; i++) {
            newGameState.getPlayerA().drawCard();
            newGameState.getPlayerB().drawCard();
        }

        return newGameState;
    }
}