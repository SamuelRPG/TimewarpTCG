package com.saroga.timewarp.application.ports.out.DeckRepository;

import com.saroga.timewarp.domain.model.entity.deck.Deck;

/** Un mazo guardado de un usuario: su nombre y su contenido (siempre 40 cartas). */
public record StoredDeck(String name, Deck deck) {
}
