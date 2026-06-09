package com.saroga.timewarp.application.service.Deck;


import com.saroga.timewarp.domain.model.enums.Faction;
import com.saroga.timewarp.infrastructure.persistence.jpa.entity.CardTemplate.CardTemplateEntity;
import com.saroga.timewarp.infrastructure.persistence.jpa.entity.Deck.DeckEntity;
import com.saroga.timewarp.infrastructure.persistence.jpa.entity.DeckCard.DeckCardEntity;
import com.saroga.timewarp.infrastructure.persistence.jpa.entity.User.UserEntity;
import com.saroga.timewarp.infrastructure.persistence.jpa.repository.CardTemplateRepository;
import com.saroga.timewarp.infrastructure.persistence.jpa.repository.DeckRepository;
import com.saroga.timewarp.infrastructure.persistence.jpa.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DeckService {

    private final DeckRepository deckRepository;
    private final UserRepository userRepository;
    private final CardTemplateRepository cardRepository;

    public DeckService(DeckRepository deckRepository, UserRepository userRepository, CardTemplateRepository cardRepository) {
        this.deckRepository = deckRepository;
        this.userRepository = userRepository;
        this.cardRepository = cardRepository;
    }

    @Transactional
    public void saveCustomDeck(UUID userId, String deckName, Map<String, Integer> cardIdsAndQuantities) {

        // 1. Verificar Usuario
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        DeckEntity newDeck = new DeckEntity();
        newDeck.setName(deckName);
        newDeck.setUser(user);

        int totalCards = 0;

        // 2. Extraer cartas y validar
        for (Map.Entry<String, Integer> entry : cardIdsAndQuantities.entrySet()) {
            CardTemplateEntity cardInfo = cardRepository.findById(entry.getKey())
                    .orElseThrow(() -> new IllegalArgumentException("Carta no existe en el catálogo: " + entry.getKey()));

            int quantity = entry.getValue();
            if (quantity > 3) {
                throw new IllegalArgumentException("No puedes tener más de 3 copias de la carta: " + cardInfo.getName());
            }

            totalCards += quantity;
            newDeck.addCard(new DeckCardEntity(cardInfo, quantity));
        }

        // 3. Regla 1: Tamaño del Mazo exacto (Ej: 40 cartas)
        if (totalCards != 40) {
            throw new IllegalArgumentException("El mazo debe tener exactamente 40 cartas. Tienes: " + totalCards);
        }

        // 4. Regla 2: Máximo 2 Facciones
        Set<Faction> uniqueFactions = newDeck.getCards().stream()
                .map(deckCard -> Faction.valueOf(deckCard.getCardTemplate().getFaction()))
                .collect(Collectors.toSet());

        if (uniqueFactions.size() > 2) {
            throw new IllegalArgumentException("Mazo inválido. Contiene " + uniqueFactions.size() +
                    " facciones distintas. El límite es 2.");
        }

        // 5. Guardar en Base de Datos Real
        deckRepository.save(newDeck);
    }
}