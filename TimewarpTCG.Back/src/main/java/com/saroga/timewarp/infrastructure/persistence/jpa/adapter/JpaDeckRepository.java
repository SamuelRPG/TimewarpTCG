package com.saroga.timewarp.infrastructure.persistence.jpa.adapter;

import com.saroga.timewarp.application.ports.out.DeckRepository.DeckRepositoryPort;
import com.saroga.timewarp.application.ports.out.DeckRepository.StoredDeck;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * IMPORTANTE: los metodos son @Transactional porque DeckEntity.cards y DeckCardEntity.cardTemplate son LAZY.
 * Desde un controlador REST funcionaba por "open-in-view", pero al iniciar una partida (mensaje WebSocket) no hay
 * sesion abierta y getDeckByName lanzaba LazyInitializationException.
 */
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
    @Transactional
    public void saveDeck(UUID userId, String deckName, Deck deck) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        // upsert: mismo usuario + mismo nombre => se reemplaza el contenido
        DeckEntity target = deckRepository.findByUserId(userId).stream()
                .filter(d -> d.getName().equalsIgnoreCase(deckName))
                .findFirst()
                .orElse(null);
        if (target == null) {
            target = new DeckEntity();
            target.setUser(user);
        } else {
            target.getCards().clear();   // orphanRemoval borra las filas antiguas
        }
        target.setName(deckName);

        // Agrupamos la lista plana del Dominio para contar cuántas copias hay de cada carta
        Map<String, Long> cardCounts = deck.getTemplates().stream()
                .collect(Collectors.groupingBy(CardTemplate::getId, LinkedHashMap::new, Collectors.counting()));

        for (Map.Entry<String, Long> entry : cardCounts.entrySet()) {
            CardTemplateEntity cardInfo = cardTemplateRepository.findById(entry.getKey())
                    .orElseThrow(() -> new IllegalArgumentException("Carta no encontrada en DB: " + entry.getKey()));
            target.addCard(new DeckCardEntity(cardInfo, entry.getValue().intValue()));
        }

        deckRepository.save(target);
    }

    @Override
    @Transactional(readOnly = true)
    public Deck getDeckByName(UUID userId, String deckName) {
        DeckEntity deckEntity = deckRepository.findByUserId(userId).stream()
                .filter(d -> d.getName().equalsIgnoreCase(deckName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Mazo no encontrado: " + deckName));
        return toDomain(deckEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getUserDeckNames(UUID userId) {
        return deckRepository.findByUserId(userId).stream()
                .map(DeckEntity::getName)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<StoredDeck> getUserDecks(UUID userId) {
        List<StoredDeck> result = new ArrayList<>();
        List<DeckEntity> entities = new ArrayList<>(deckRepository.findByUserId(userId));
        entities.sort(Comparator.comparing(DeckEntity::getName, String.CASE_INSENSITIVE_ORDER));
        for (DeckEntity e : entities) {
            try {
                result.add(new StoredDeck(e.getName(), toDomain(e)));
            } catch (IllegalArgumentException invalidLegacyDeck) {
                // mazo antiguo que no suma 40 cartas: no se puede usar, se omite
                System.out.println("[DECKS] Se omite el mazo '" + e.getName() + "': " + invalidLegacyDeck.getMessage());
            }
        }
        return result;
    }

    @Override
    @Transactional
    public boolean deleteDeck(UUID userId, String deckName) {
        DeckEntity target = deckRepository.findByUserId(userId).stream()
                .filter(d -> d.getName().equalsIgnoreCase(deckName))
                .findFirst()
                .orElse(null);
        if (target == null) return false;
        deckRepository.delete(target);
        return true;
    }

    private Deck toDomain(DeckEntity deckEntity) {
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
}
