package com.saroga.timewarp.application.ports.out.DeckRepository;

import com.saroga.timewarp.domain.model.entity.deck.Deck;
import java.util.List;
import java.util.UUID;

public interface DeckRepositoryPort {
    /** Guarda el mazo. Si el usuario ya tiene uno con ese nombre (sin distinguir mayusculas) lo SUSTITUYE. */
    void saveDeck(UUID userId, String deckName, Deck deck);
    Deck getDeckByName(UUID userId, String deckName);
    List<String> getUserDeckNames(UUID userId); // Devuelve solo los nombres para el menú
    List<StoredDeck> getUserDecks(UUID userId);  // Todos los mazos con su contenido, ordenados por nombre
    boolean deleteDeck(UUID userId, String deckName);
}
