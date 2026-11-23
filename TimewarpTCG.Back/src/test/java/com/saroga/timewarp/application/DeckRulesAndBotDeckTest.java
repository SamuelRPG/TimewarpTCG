package com.saroga.timewarp.application;

import com.saroga.timewarp.application.factory.DeckFactory;
import com.saroga.timewarp.application.ports.out.DeckRepository.DeckRepositoryPort;
import com.saroga.timewarp.application.ports.out.DeckRepository.StoredDeck;
import com.saroga.timewarp.application.service.BotDeck.BotDeckService;
import com.saroga.timewarp.application.service.Deck.DeckService;
import com.saroga.timewarp.domain.model.entity.cardTemplate.CardTemplate;
import com.saroga.timewarp.domain.model.entity.deck.Deck;
import com.saroga.timewarp.domain.model.enums.CardType;
import com.saroga.timewarp.domain.model.enums.Faction;
import com.saroga.timewarp.domain.model.enums.SpellSpeed;
import com.saroga.timewarp.domain.model.vo.card.Cost;
import com.saroga.timewarp.domain.model.vo.card.Drive;
import com.saroga.timewarp.domain.repository.cardCatalog.CardCatalogRepository;
import com.saroga.timewarp.domain.service.DeckValidatorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Reglas de mazo (40 cartas, 3 copias, 2 facciones) y mazo aleatorio del bot. Sin Spring ni base de datos. */
public class DeckRulesAndBotDeckTest {

    // ---- catalogo falso: 4 facciones x 9 cartas (6 unidades + 3 hechizos)
    static class FakeCatalog implements CardCatalogRepository {
        final List<CardTemplate> cards = new ArrayList<>();
        FakeCatalog(int perFaction) {
            for (Faction f : Faction.values()) {
                for (int i = 0; i < perFaction; i++) {
                    boolean spell = i >= perFaction - 3;
                    cards.add(new CardTemplate(f.name() + "_" + i, "Carta " + i, "", Drive.of(spell ? 0 : 2 + i), Cost.of(1 + i % 5),
                            spell ? CardType.SPELL : CardType.UNIT, spell ? SpellSpeed.FAST : SpellSpeed.NOT_A_SPELL,
                            new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), f));
                }
            }
        }
        public Optional<CardTemplate> findById(String id) { return cards.stream().filter(c -> c.getId().equals(id)).findFirst(); }
        public List<CardTemplate> findAll() { return cards; }
    }

    static class FakeDecks implements DeckRepositoryPort {
        final Map<String, Deck> decks = new LinkedHashMap<>();
        public void saveDeck(UUID u, String n, Deck d) { decks.keySet().removeIf(k -> k.equalsIgnoreCase(n)); decks.put(n, d); }
        public Deck getDeckByName(UUID u, String n) { return decks.get(n); }
        public List<String> getUserDeckNames(UUID u) { return new ArrayList<>(decks.keySet()); }
        public List<StoredDeck> getUserDecks(UUID u) { return decks.entrySet().stream().map(e -> new StoredDeck(e.getKey(), e.getValue())).collect(Collectors.toList()); }
        public boolean deleteDeck(UUID u, String n) { return decks.remove(n) != null; }
    }

    private FakeCatalog catalog;
    private FakeDecks decks;
    private DeckService service;
    private final UUID user = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        catalog = new FakeCatalog(9);
        decks = new FakeDecks();
        service = new DeckService(decks, catalog, new DeckValidatorService());
    }

    private Map<String, Integer> legalDeck(Faction a, Faction b) {
        Map<String, Integer> m = new LinkedHashMap<>();
        int total = 0;
        for (CardTemplate c : catalog.cards) {
            if (c.getFaction() != a && c.getFaction() != b) continue;
            int q = Math.min(3, 40 - total);
            if (q <= 0) break;
            m.put(c.getId(), q);
            total += q;
        }
        assertEquals(40, total);
        return m;
    }

    @Test
    void savesALegalDeck() {
        service.saveCustomDeck(user, "  Mi mazo  ", legalDeck(Faction.ROMANS, Faction.STEAMPUNK));
        assertTrue(decks.decks.containsKey("Mi mazo"), "el nombre se recorta");
        assertEquals(40, decks.decks.get("Mi mazo").getTemplates().size());
    }

    @Test
    void savingSameNameReplacesInsteadOfDuplicating() {
        service.saveCustomDeck(user, "X", legalDeck(Faction.ROMANS, Faction.STEAMPUNK));
        service.saveCustomDeck(user, "x", legalDeck(Faction.GLITCHPOP, Faction.ANCIENT_EGYPT));
        assertEquals(1, decks.decks.size());
    }

    @Test
    void rejectsDeckWithNot40Cards() {
        Map<String, Integer> m = legalDeck(Faction.ROMANS, Faction.STEAMPUNK);
        String first = m.keySet().iterator().next();
        m.put(first, m.get(first) - 1);   // 39
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> service.saveCustomDeck(user, "X", m));
        assertTrue(e.getMessage().contains("40"), e.getMessage());
        assertTrue(decks.decks.isEmpty());
    }

    @Test
    void rejectsMoreThanThreeCopies() {
        Map<String, Integer> m = legalDeck(Faction.ROMANS, Faction.STEAMPUNK);
        m.put(m.keySet().iterator().next(), 4);
        assertThrows(IllegalArgumentException.class, () -> service.saveCustomDeck(user, "X", m));
    }

    @Test
    void rejectsThreeFactions() {
        // 9 cartas romanas x3 = 27, 4 steampunk x3 = 12, 1 glitchpop x1 = 1  => 40 cartas pero 3 facciones
        Map<String, Integer> m = new LinkedHashMap<>();
        int rm = 0, sp = 0;
        for (CardTemplate c : catalog.cards) {
            if (c.getFaction() == Faction.ROMANS) m.put(c.getId(), 3);
            else if (c.getFaction() == Faction.STEAMPUNK && sp++ < 4) m.put(c.getId(), 3);
            else if (c.getFaction() == Faction.GLITCHPOP && rm++ < 1) m.put(c.getId(), 1);
        }
        assertEquals(40, m.values().stream().mapToInt(Integer::intValue).sum());
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> service.saveCustomDeck(user, "X", m));
        assertTrue(e.getMessage().toLowerCase().contains("facciones"), e.getMessage());
    }

    @Test
    void rejectsUnknownCardEmptyNameAndReservedNames() {
        Map<String, Integer> bad = new LinkedHashMap<>(Map.of("NO_EXISTE", 3));
        assertThrows(IllegalArgumentException.class, () -> service.saveCustomDeck(user, "X", bad));
        Map<String, Integer> ok = legalDeck(Faction.ROMANS, Faction.STEAMPUNK);
        assertThrows(IllegalArgumentException.class, () -> service.saveCustomDeck(user, "   ", ok));
        assertThrows(IllegalArgumentException.class, () -> service.saveCustomDeck(user, "aggro", ok));
        assertThrows(IllegalArgumentException.class, () -> service.saveCustomDeck(user, "Aleatorio", ok));
        assertThrows(IllegalArgumentException.class, () -> service.saveCustomDeck(user, "x".repeat(31), ok));
    }

    @Test
    void randomBotDecksAreAlwaysLegalAndVaried() {
        BotDeckService bot = new BotDeckService(catalog, new DeckFactory(), new DeckValidatorService(), new Random(42));
        Set<String> signatures = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            Deck d = bot.randomDeck();
            List<CardTemplate> t = d.getTemplates();
            assertEquals(40, t.size());
            Map<String, Long> copies = t.stream().collect(Collectors.groupingBy(CardTemplate::getId, Collectors.counting()));
            assertTrue(copies.values().stream().allMatch(c -> c <= 3), "mas de 3 copias: " + copies);
            assertTrue(t.stream().map(CardTemplate::getFaction).distinct().count() <= 2, "mas de 2 facciones");
            long units = t.stream().filter(c -> c.getCardType() == CardType.UNIT).count();
            assertTrue(units >= 24, "demasiados pocos unidades: " + units);
            long spells = 40 - units;
            assertTrue(spells >= 6 && spells <= 12, "el mazo debe llevar entre 6 y 12 hechizos; lleva " + spells);
            signatures.add(new TreeMap<>(copies).toString());
        }
        assertTrue(signatures.size() > 100, "los mazos deberian variar; distintos: " + signatures.size());
    }

    @Test
    void botDeckFallsBackToFactoryDecksWhenCatalogIsTooSmall() {
        BotDeckService bot = new BotDeckService(new FakeCatalog(3), new DeckFactory(), new DeckValidatorService(), new Random(1));
        assertEquals(40, bot.randomDeck().getTemplates().size());
    }

    @Test
    void botDeckResolveHonoursExplicitNames() {
        BotDeckService bot = new BotDeckService(catalog, new DeckFactory(), new DeckValidatorService(), new Random(7));
        assertEquals("U_AE_02", bot.resolve("aggro").getTemplates().stream().map(CardTemplate::getId).filter(id -> id.equals("U_AE_02")).findFirst().orElse(null));
        assertTrue(bot.resolve("CONTROL").getTemplates().stream().anyMatch(c -> c.getCardType() == CardType.SPELL));
        assertEquals(40, bot.resolve(null).getTemplates().size());
        assertEquals(40, bot.resolve("RANDOM").getTemplates().size());
    }
}
