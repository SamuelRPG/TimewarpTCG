package com.saroga.timewarp.application;

import com.saroga.timewarp.application.factory.DeckFactory;
import com.saroga.timewarp.application.service.BotDeck.BotDeckService;
import com.saroga.timewarp.application.service.StartMatch.StartMatchService;
import com.saroga.timewarp.domain.model.entity.card.Card;
import com.saroga.timewarp.domain.model.entity.gameState.GameState;
import com.saroga.timewarp.domain.service.DeckValidatorService;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Los mazos se barajan al empezar la partida (antes se robaban en el orden de la lista: 25 infanterias seguidas). */
public class StartMatchShuffleTest {

    @Test
    void decksAreShuffledAndMatchStartsWith4CardsEach() {
        DeckRulesAndBotDeckTest.FakeCatalog catalog = new DeckRulesAndBotDeckTest.FakeCatalog(9);
        StartMatchService start = new StartMatchService(new DeckRulesAndBotDeckTest.FakeDecks(), new DeckFactory(),
                new BotDeckService(catalog, new DeckFactory(), new DeckValidatorService()));

        Set<String> firstHands = new HashSet<>();
        boolean sawVanguardiaInOpeningHand = false;
        for (int i = 0; i < 60; i++) {
            GameState g = start.executeLocalMatch(UUID.randomUUID(), "AGGRO", "CONTROL");
            assertEquals(4, g.getPlayerA().getHand().size());
            assertEquals(4, g.getPlayerB().getHand().size());
            assertEquals(36, g.getPlayerA().getDeck().size());
            firstHands.add(g.getPlayerA().getHand().stream().map(Card::getName).sorted().collect(Collectors.joining("|")));
            if (g.getPlayerA().getHand().stream().anyMatch(c -> c.getName().equals("Vanguardia Pesada"))) sawVanguardiaInOpeningHand = true;
        }
        // Sin barajar, la mano inicial de AGGRO seria SIEMPRE 4 Infanterias Rapidas (una sola combinacion)
        assertTrue(sawVanguardiaInOpeningHand, "con el mazo barajado debe salir alguna Vanguardia Pesada en la mano inicial");
        assertTrue(firstHands.size() >= 3, "las manos iniciales deben variar; distintas: " + firstHands);
    }

    @Test
    void botWithControlDeckGetsSpellsEarly() {
        StartMatchService start = new StartMatchService(new DeckRulesAndBotDeckTest.FakeDecks(), new DeckFactory(),
                new BotDeckService(new DeckRulesAndBotDeckTest.FakeCatalog(9), new DeckFactory(), new DeckValidatorService()));
        int gamesWithSpellInFirst4 = 0;
        for (int i = 0; i < 60; i++) {
            GameState g = start.executeLocalMatch(UUID.randomUUID(), "AGGRO", "CONTROL");
            if (g.getPlayerB().getHand().stream().anyMatch(Card::isSpell)) gamesWithSpellInFirst4++;
        }
        assertTrue(gamesWithSpellInFirst4 > 20, "el bot CONTROL (50% hechizos) deberia verlos pronto; partidas con hechizo: " + gamesWithSpellInFirst4);
    }
}
