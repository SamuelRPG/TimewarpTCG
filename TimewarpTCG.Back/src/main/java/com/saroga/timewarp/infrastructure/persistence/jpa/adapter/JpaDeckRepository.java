package com.saroga.timewarp.infrastructure.persistence.jpa.adapter;

import com.saroga.timewarp.application.ports.out.DeckRepository.DeckRepositoryPort;
import com.saroga.timewarp.domain.model.entity.cardTemplate.CardTemplate;
import com.saroga.timewarp.domain.model.entity.deck.Deck;
import com.saroga.timewarp.infrastructure.persistence.jpa.entity.CardTemplate.CardTemplateEntity;
import com.saroga.timewarp.infrastructure.persistence.jpa.entity.Deck.DeckEntity;
import com.saroga.timewarp.infrastructure.persistence.jpa.entity.DeckCard.DeckCardEntity;
import com.saroga.timewarp.infrastructure.persistence.jpa.entity.User.UserEntity;
import com.saroga.timewarp.infrastructure.persistence.jpa.mapper.CardTemplateMapper;
import com.saroga.timewarp.infrastructure.persistence.jpa.repository.CardTemplateRepository;
import com.saroga.timewarp.infrastructure.persistence.jpa.repository.DeckRepository;
import com.saroga.timewarp.infrastructure.persistence.jpa.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class JpaDeckRepository implements DeckRepositoryPort {

    private final DeckRepository deckRepository;
    private final UserRepository userRepository;
    private final CardTemplateRepository cardTemplateRepository;
    private final CardTemplateMapper cardMapper;

    // Aquí todo es infraestructura, por lo que es totalmente legal inyectar repositorios JPA y el Mapper
    public JpaDeckRepository(DeckRepository deckRepository, UserRepository userRepository,
                                    CardTemplateRepository cardTemplateRepository, CardTemplateMapper cardMapper) {
        this.deckRepository = deckRepository;
        this.userRepository = userRepository;
        this.cardTemplateRepository = cardTemplateRepository;
        this.cardMapper = cardMapper;
    }

    @Override
    public void saveDeck(UUID userId, String deckName, Deck deck) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        DeckEntity newDeck = new DeckEntity();
        newDeck.setName(deckName);
        newDeck.setUser(user);

        // Agrupamos la lista plana del Dominio para contar cuántas copias hay de cada carta
        Map<String, Long> cardCounts = deck.getTemplates().stream()
                .collect(Collectors.groupingBy(CardTemplate::getId, Collectors.counting()));

        for (Map.Entry<String, Long> entry : cardCounts.entrySet()) {
            CardTemplateEntity cardInfo = cardTemplateRepository.findById(entry.getKey())
                    .orElseThrow(() -> new IllegalArgumentException("Carta no encontrada en DB"));

            newDeck.addCard(new DeckCardEntity(cardInfo, entry.getValue().intValue()));
        }

        deckRepository.save(newDeck);
    }

    @Override
    public Deck getDeckByName(UUID userId, String deckName) {
        DeckEntity deckEntity = deckRepository.findByUserId(userId).stream()
                .filter(d -> d.getName().equalsIgnoreCase(deckName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Mazo no encontrado: " + deckName));

        List<CardTemplate> flatList = new ArrayList<>();
        for (DeckCardEntity groupedCard : deckEntity.getCards()) {
            // Utilizamos el Mapper de infraestructura
            CardTemplate domainTemplate = cardMapper.toDomain(groupedCard.getCardTemplate());
            // Descomprimimos
            for (int i = 0; i < groupedCard.getQuantity(); i++) {
                flatList.add(domainTemplate);
            }
        }
        return new Deck(flatList); // Devolvemos el objeto puro al Dominio
    }

    @Override
    public List<String> getUserDeckNames(UUID userId) {
        return deckRepository.findByUserId(userId).stream()
                .map(DeckEntity::getName)
                .collect(Collectors.toList());
    }
}
