package com.saroga.timewarp.application.service.BotDeck;

import com.saroga.timewarp.application.factory.DeckFactory;
import com.saroga.timewarp.domain.model.entity.cardTemplate.CardTemplate;
import com.saroga.timewarp.domain.model.entity.deck.Deck;
import com.saroga.timewarp.domain.model.enums.CardType;
import com.saroga.timewarp.domain.model.enums.Faction;
import com.saroga.timewarp.domain.repository.cardCatalog.CardCatalogRepository;
import com.saroga.timewarp.domain.service.DeckValidatorService;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Elige el mazo del bot. Por defecto genera uno ALEATORIO LEGAL a partir del catalogo:
 * 40 cartas, maximo 3 copias de cada una y maximo 2 facciones (las mismas reglas que el jugador).
 * Si el catalogo es demasiado pequeño para armar un mazo legal, usa los mazos de prueba (AGGRO / CONTROL).
 */
public class BotDeckService {

    public static final int DECK_SIZE = 40;
    public static final int MAX_COPIES = 3;
    /** Con 3 copias por carta hacen falta al menos 14 cartas distintas para llegar a 40. */
    public static final int MIN_DISTINCT_CARDS = 14;

    private final CardCatalogRepository catalog;
    private final DeckFactory deckFactory;
    private final DeckValidatorService validator;
    private final Random rnd;

    public BotDeckService(CardCatalogRepository catalog, DeckFactory deckFactory, DeckValidatorService validator) {
        this(catalog, deckFactory, validator, new Random());
    }

    public BotDeckService(CardCatalogRepository catalog, DeckFactory deckFactory, DeckValidatorService validator, Random rnd) {
        this.catalog = catalog;
        this.deckFactory = deckFactory;
        this.validator = validator;
        this.rnd = rnd;
    }

    /** "AGGRO" / "CONTROL" -> mazo de prueba; cualquier otra cosa (null, "", "RANDOM", "ALEATORIO") -> aleatorio. */
    public Deck resolve(String botDeckName) {
        if (botDeckName != null) {
            String n = botDeckName.trim();
            if (n.equalsIgnoreCase("AGGRO") || n.equalsIgnoreCase("CONTROL")) {
                System.out.println("[BOT] Mazo del bot: " + n.toUpperCase() + " (mazo de prueba)");
                return deckFactory.getDeckByName(n);
            }
        }
        return randomDeck();
    }

    public Deck randomDeck() {
        try {
            List<CardTemplate> all = catalog.findAll();
            Map<Faction, List<CardTemplate>> byFaction = all.stream().collect(Collectors.groupingBy(CardTemplate::getFaction));
            List<Faction> factions = new ArrayList<>(byFaction.keySet());
            Collections.shuffle(factions, rnd);

            // Pares de facciones (en orden aleatorio) cuyo conjunto da para un mazo legal
            List<List<Faction>> options = new ArrayList<>();
            for (int i = 0; i < factions.size(); i++) {
                for (int j = i + 1; j < factions.size(); j++) {
                    if (byFaction.get(factions.get(i)).size() + byFaction.get(factions.get(j)).size() >= MIN_DISTINCT_CARDS)
                        options.add(List.of(factions.get(i), factions.get(j)));
                }
            }
            for (Faction f : factions) {
                if (byFaction.get(f).size() >= MIN_DISTINCT_CARDS) options.add(List.of(f));
            }
            if (options.isEmpty()) return fallback("catalogo insuficiente para un mazo legal");

            List<Faction> chosen = options.get(0);
            List<CardTemplate> pool = new ArrayList<>();
            chosen.forEach(f -> pool.addAll(byFaction.get(f)));

            Deck deck = build(pool);
            validator.validate(deck);
            System.out.println("[BOT] Mazo aleatorio: " + chosen + " (" + describe(deck) + ")");
            return deck;
        } catch (RuntimeException e) {
            return fallback("error generando mazo: " + e.getMessage());
        }
    }

    private Deck build(List<CardTemplate> pool) {
        List<CardTemplate> units = pool.stream().filter(c -> c.getCardType() == CardType.UNIT).collect(Collectors.toList());
        List<CardTemplate> spells = pool.stream().filter(c -> c.getCardType() == CardType.SPELL).collect(Collectors.toList());

        int spellTarget = spells.isEmpty() ? 0 : 6 + rnd.nextInt(7);      // 6..12 hechizos
        List<CardTemplate> deck = new ArrayList<>();
        Map<String, Integer> copies = new HashMap<>();

        fill(deck, copies, units, DECK_SIZE - spellTarget);
        fill(deck, copies, spells, DECK_SIZE);                            // el parametro es el tamaño FINAL del mazo, no el nº de hechizos
        fill(deck, copies, pool, DECK_SIZE);                              // rellena lo que falte con lo que sea

        if (deck.size() != DECK_SIZE) throw new IllegalStateException("no se pudo completar el mazo (" + deck.size() + ")");
        return new Deck(deck);
    }

    /** Añade copias (1..3 por carta, al azar) de las cartas de 'source' hasta que el mazo llegue a 'targetSize'. */
    private void fill(List<CardTemplate> deck, Map<String, Integer> copies, List<CardTemplate> source, int targetSize) {
        List<CardTemplate> shuffled = new ArrayList<>(source);
        boolean progress = true;
        while (deck.size() < targetSize && progress) {
            progress = false;
            Collections.shuffle(shuffled, rnd);
            for (CardTemplate t : shuffled) {
                if (deck.size() >= targetSize) break;
                int have = copies.getOrDefault(t.getId(), 0);
                int room = Math.min(MAX_COPIES - have, targetSize - deck.size());
                if (room <= 0) continue;
                int add = Math.min(room, 1 + rnd.nextInt(MAX_COPIES));
                for (int i = 0; i < add; i++) deck.add(t);
                copies.put(t.getId(), have + add);
                progress = true;
            }
        }
    }

    private Deck fallback(String why) {
        String name = rnd.nextBoolean() ? "AGGRO" : "CONTROL";
        System.out.println("[BOT] Mazo de prueba " + name + " (" + why + ")");
        return deckFactory.getDeckByName(name);
    }

    private static String describe(Deck deck) {
        long units = deck.getTemplates().stream().filter(c -> c.getCardType() == CardType.UNIT).count();
        return units + " unidades, " + (deck.getTemplates().size() - units) + " hechizos";
    }
}
