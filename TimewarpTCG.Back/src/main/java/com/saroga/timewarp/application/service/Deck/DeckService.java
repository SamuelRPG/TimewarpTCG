package com.saroga.timewarp.application.service.Deck;

import com.saroga.timewarp.application.ports.out.DeckRepository.DeckRepositoryPort;
import com.saroga.timewarp.domain.model.entity.cardTemplate.CardTemplate;
import com.saroga.timewarp.domain.model.entity.deck.Deck;
import com.saroga.timewarp.domain.repository.cardCatalog.CardCatalogRepository;
import com.saroga.timewarp.domain.service.DeckValidatorService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DeckService {

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

    public void saveCustomDeck(UUID userId, String deckName, Map<String, Integer> cardIdsAndQuantities) {
        List<CardTemplate> flatCardList = new ArrayList<>();

        // 1. Extraemos los objetos de Dominio a través del puerto del catálogo
        for (Map.Entry<String, Integer> entry : cardIdsAndQuantities.entrySet()) {
            String cardId = entry.getKey();
            int quantity = entry.getValue();

            if (quantity > 3) {
                throw new IllegalArgumentException("No puedes tener más de 3 copias de la carta: " + cardId);
            }

            CardTemplate template = cardCatalogRepository.findById(cardId)
                    .orElseThrow(() -> new IllegalArgumentException("Carta no existe en el catálogo: " + cardId));

            for (int i = 0; i < quantity; i++) {
                flatCardList.add(template);
            }
        }

        // 2. Montamos el mazo y validamos las reglas de negocio (40 cartas, máx 2 facciones)
        Deck newDeck = new Deck(flatCardList);
        deckValidatorService.validate(newDeck);

        // 3. Enviamos a guardar al puerto
        deckRepositoryPort.saveDeck(userId, deckName, newDeck);
    }

    public Deck getDomainDeck(UUID userId, String deckName) {
        return deckRepositoryPort.getDeckByName(userId, deckName);
    }

    public List<String> getUserDeckNames(UUID userId) {
        return deckRepositoryPort.getUserDeckNames(userId);
    }
}