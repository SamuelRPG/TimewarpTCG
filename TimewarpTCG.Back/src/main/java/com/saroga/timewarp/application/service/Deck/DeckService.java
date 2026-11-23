package com.saroga.timewarp.application.service.Deck;

import com.saroga.timewarp.application.ports.out.DeckRepository.DeckRepositoryPort;
import com.saroga.timewarp.application.ports.out.DeckRepository.StoredDeck;
import com.saroga.timewarp.domain.model.entity.cardTemplate.CardTemplate;
import com.saroga.timewarp.domain.model.entity.deck.Deck;
import com.saroga.timewarp.domain.repository.cardCatalog.CardCatalogRepository;
import com.saroga.timewarp.domain.service.DeckValidatorService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class DeckService {

    public static final int DECK_SIZE = 40;
    public static final int MAX_COPIES = 3;
    public static final int MAX_DECKS_PER_USER = 30;
    public static final int MAX_NAME_LENGTH = 30;
    /** Nombres que usa el sistema para los mazos de prueba / mazo aleatorio del bot. */
    public static final Set<String> RESERVED_NAMES = Set.of("AGGRO", "CONTROL", "RANDOM", "ALEATORIO");

    private final DeckRepositoryPort deckRepositoryPort;
    private final CardCatalogRepository cardCatalogRepository;
    private final DeckValidatorService deckValidatorService;

    // Solo inyectamos Puertos y Servicios de Dominio
    public DeckService(DeckRepositoryPort deckRepositoryPort,
                       CardCatalogRepository cardCatalogRepository,
                       DeckValidatorService deckValidatorService) {
        this.deckRepositoryPort = deckRepositoryPort;
        this.cardCatalogRepository = cardCatalogRepository;
        this.deckValidatorService = deckValidatorService;
    }

    /**
     * Valida y guarda (o sustituye, si ya existe uno con ese nombre) un mazo personalizado.
     * Reglas: exactamente 40 cartas, maximo 3 copias de cada una, maximo 2 facciones.
     * Lanza IllegalArgumentException con un mensaje apto para mostrar al jugador.
     */
    public String saveCustomDeck(UUID userId, String deckName, Map<String, Integer> cardIdsAndQuantities) {
        String name = normalizeName(deckName);
        if (cardIdsAndQuantities == null || cardIdsAndQuantities.isEmpty()) {
            throw new IllegalArgumentException("El mazo está vacío.");
        }

        List<CardTemplate> flatCardList = new ArrayList<>();

        // 1. Extraemos los objetos de Dominio a través del puerto del catálogo
        for (Map.Entry<String, Integer> entry : cardIdsAndQuantities.entrySet()) {
            String cardId = entry.getKey();
            int quantity = entry.getValue() == null ? 0 : entry.getValue();

            if (quantity < 0) {
                throw new IllegalArgumentException("Cantidad no válida para la carta " + cardId);
            }
            if (quantity == 0) continue;
            if (quantity > MAX_COPIES) {
                throw new IllegalArgumentException("No puedes tener más de " + MAX_COPIES + " copias de la carta: " + cardId);
            }

            CardTemplate template = cardCatalogRepository.findById(cardId)
                    .orElseThrow(() -> new IllegalArgumentException("Carta no existe en el catálogo: " + cardId));

            for (int i = 0; i < quantity; i++) {
                flatCardList.add(template);
            }
        }

        // 2. Montamos el mazo y validamos las reglas de negocio (40 cartas, máx 2 facciones)
        if (flatCardList.size() != DECK_SIZE) {
            throw new IllegalArgumentException("El mazo debe tener exactamente " + DECK_SIZE + " cartas (ahora tiene " + flatCardList.size() + ").");
        }
        Deck newDeck = new Deck(flatCardList);
        deckValidatorService.validate(newDeck);

        // 3. Limite de mazos por usuario (solo si es uno nuevo)
        boolean exists = deckRepositoryPort.getUserDeckNames(userId).stream().anyMatch(n -> n.equalsIgnoreCase(name));
        if (!exists && deckRepositoryPort.getUserDeckNames(userId).size() >= MAX_DECKS_PER_USER) {
            throw new IllegalArgumentException("Has alcanzado el máximo de " + MAX_DECKS_PER_USER + " mazos. Borra alguno.");
        }

        // 4. Enviamos a guardar al puerto
        deckRepositoryPort.saveDeck(userId, name, newDeck);
        return name;
    }

    public static String normalizeName(String deckName) {
        String name = deckName == null ? "" : deckName.trim();
        if (name.isEmpty()) throw new IllegalArgumentException("El mazo necesita un nombre.");
        if (name.length() > MAX_NAME_LENGTH) throw new IllegalArgumentException("El nombre del mazo no puede superar " + MAX_NAME_LENGTH + " caracteres.");
        if (RESERVED_NAMES.contains(name.toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("'" + name + "' es un nombre reservado. Elige otro.");
        }
        return name;
    }

    public Deck getDomainDeck(UUID userId, String deckName) {
        return deckRepositoryPort.getDeckByName(userId, deckName);
    }

    public List<String> getUserDeckNames(UUID userId) {
        return deckRepositoryPort.getUserDeckNames(userId);
    }

    public List<StoredDeck> getUserDecks(UUID userId) {
        return deckRepositoryPort.getUserDecks(userId);
    }

    public boolean deleteDeck(UUID userId, String deckName) {
        return deckRepositoryPort.deleteDeck(userId, deckName);
    }
}
