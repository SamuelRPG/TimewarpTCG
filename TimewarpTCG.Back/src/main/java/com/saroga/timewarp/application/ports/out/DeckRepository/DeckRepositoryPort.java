package com.saroga.timewarp.application.ports.out.DeckRepository;

import com.saroga.timewarp.domain.model.entity.deck.Deck;
import java.util.List;
import java.util.UUID;

public interface DeckRepositoryPort {
    void saveDeck(UUID userId, String deckName, Deck deck);
    Deck getDeckByName(UUID userId, String deckName);
    List<String> getUserDeckNames(UUID userId); // Devuelve solo los nombres para el menú
}