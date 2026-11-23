package com.saroga.timewarp.application.service.StartMatch;

import com.saroga.timewarp.application.factory.DeckFactory;
import com.saroga.timewarp.application.ports.in.StartMatch.StartMatchUseCase;
import com.saroga.timewarp.application.ports.out.DeckRepository.DeckRepositoryPort;
import com.saroga.timewarp.application.service.BotDeck.BotDeckService;
import com.saroga.timewarp.domain.model.entity.card.Card;
import com.saroga.timewarp.domain.model.entity.deck.Deck;
import com.saroga.timewarp.domain.model.entity.gameState.GameState;
import com.saroga.timewarp.domain.model.entity.playerState.PlayerState;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

public class StartMatchService implements StartMatchUseCase {

    private final DeckRepositoryPort deckRepositoryPort;
    private final DeckFactory deckFactory;
    private final BotDeckService botDeckService;

    // Inyectamos el puerto a BBDD, la fábrica de mazos de prueba y el selector de mazo del bot
    public StartMatchService(DeckRepositoryPort deckRepositoryPort, DeckFactory deckFactory, BotDeckService botDeckService) {
        this.deckRepositoryPort = deckRepositoryPort;
        this.deckFactory = deckFactory;
        this.botDeckService = botDeckService;
    }

    @Override
    public GameState executeLocalMatch(UUID playerId, String chosenDeckName, String botDeckName) {
        if (chosenDeckName == null || chosenDeckName.isBlank()) {
            throw new IllegalArgumentException("Elige un mazo para jugar.");
        }

        Deck playerDeck;

        // MEJORA: Si el frontend pide un mazo de prueba, usamos la fábrica simulada
        if (chosenDeckName.equalsIgnoreCase("AGGRO") || chosenDeckName.equalsIgnoreCase("CONTROL")) {
            playerDeck = deckFactory.getDeckByName(chosenDeckName);
        } else {
            // Si es un mazo personalizado, obligatoriamente lo buscamos en Supabase
            playerDeck = deckRepositoryPort.getDeckByName(playerId, chosenDeckName);
        }

        // El bot usa un mazo aleatorio legal del catálogo (o AGGRO/CONTROL si se pide expresamente)
        Deck botDeck = botDeckService.resolve(botDeckName);
        UUID botId = UUID.randomUUID();

        // 2. CONVERSIÓN: Leemos la receta y fabricamos las cartas vivas (List<Card>)
        List<Card> livePlayerCards = playerDeck.getTemplates().stream()
                .map(template -> Card.fromTemplate(template, playerId))
                .collect(Collectors.toList());

        List<Card> liveBotCards = botDeck.getTemplates().stream()
                .map(template -> Card.fromTemplate(template, botId))
                .collect(Collectors.toList());

        // BARAJAR: drawCard() roba siempre la primera carta de la lista y las listas salen agrupadas por carta
        // (25 infanterias seguidas, 20 muros y luego los hechizos...). Sin barajar, nadie veia un hechizo hasta el turno ~20.
        Collections.shuffle(livePlayerCards);
        Collections.shuffle(liveBotCards);

        boolean hasInitialPriority = new Random().nextBoolean();

        // 3. Entregamos la lista de cartas vivas al PlayerState
        PlayerState pA = new PlayerState(playerId, livePlayerCards, hasInitialPriority, false);
        PlayerState pB = new PlayerState(botId, liveBotCards, !hasInitialPriority, true);

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