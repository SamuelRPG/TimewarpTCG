package com.saroga.timewarp.application;

import com.saroga.timewarp.application.factory.DeckFactory;
import com.saroga.timewarp.application.service.BotDeck.BotDeckService;
import com.saroga.timewarp.application.service.StartMatch.StartMatchService;
import com.saroga.timewarp.domain.model.entity.cardTemplate.CardTemplate;
import com.saroga.timewarp.domain.model.entity.deck.Deck;
import com.saroga.timewarp.domain.model.enums.Faction;
import com.saroga.timewarp.domain.service.DeckValidatorService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** La regla "como maximo 2 facciones" tambien se aplica AL EMPEZAR la partida, no solo al guardar el mazo. */
public class StartMatchFactionRuleTest {

    private final DeckRulesAndBotDeckTest.FakeCatalog catalog = new DeckRulesAndBotDeckTest.FakeCatalog(9);
    private final DeckRulesAndBotDeckTest.FakeDecks decks = new DeckRulesAndBotDeckTest.FakeDecks();
    private final StartMatchService start = new StartMatchService(decks, new DeckFactory(),
            new BotDeckService(catalog, new DeckFactory(), new DeckValidatorService()), new DeckValidatorService());
    private final UUID me = UUID.randomUUID();

    /** 40 cartas repartidas entre las facciones indicadas (se meten directo en el "repositorio": como si alguien tocara la base de datos). */
    private Deck deckOf(Faction... factions) {
        List<CardTemplate> cards = new ArrayList<>();
        int i = 0;
        while (cards.size() < 40) {
            Faction f = factions[i++ % factions.length];
            for (CardTemplate c : catalog.cards) if (c.getFaction() == f && cards.size() < 40 && i % 2 == 0) { cards.add(c); }
            if (i > 400) break;
            for (CardTemplate c : catalog.cards) if (c.getFaction() == f && cards.size() < 40) { cards.add(c); break; }
        }
        return new Deck(cards);
    }

    @Test
    void aStoredDeckWithThreeFactionsCannotStartAMatch_neitherVsBotNorVsAPerson() {
        decks.decks.put("Trampa", deckOf(Faction.ROMANS, Faction.STEAMPUNK, Faction.GLITCHPOP));
        long factions = decks.decks.get("Trampa").getTemplates().stream().map(CardTemplate::getFaction).distinct().count();
        assertEquals(3, factions, "el mazo de prueba debe tener 3 facciones");

        var e1 = assertThrows(IllegalArgumentException.class, () -> start.executeLocalMatch(me, "Trampa", "RANDOM"));
        assertTrue(e1.getMessage().contains("facciones"), e1.getMessage());
        assertThrows(IllegalArgumentException.class, () -> start.checkDeck(me, "Trampa"));
        assertThrows(IllegalArgumentException.class, () -> start.executeVersusMatch(me, "AGGRO", UUID.randomUUID(), "Trampa"));
    }

    @Test
    void aStoredDeckWithTwoFactionsPlays() {
        decks.decks.put("Legal", deckOf(Faction.ROMANS, Faction.STEAMPUNK));
        assertDoesNotThrow(() -> start.checkDeck(me, "Legal"));
        assertNotNull(start.executeLocalMatch(me, "Legal", "AGGRO"));
        assertNotNull(start.executeVersusMatch(me, "Legal", UUID.randomUUID(), "CONTROL"));
    }

    @Test
    void aMissingDeckGivesAClearMessage() {
        var e = assertThrows(IllegalArgumentException.class, () -> start.checkDeck(me, "NoExiste"));
        assertTrue(e.getMessage().contains("NoExiste"), e.getMessage());
    }
}
