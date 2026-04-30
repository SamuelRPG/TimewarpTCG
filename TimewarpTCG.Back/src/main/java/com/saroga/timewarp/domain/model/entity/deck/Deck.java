package com.saroga.timewarp.domain.model.entity.deck;

import com.saroga.timewarp.domain.model.entity.card.Card;
import java.util.List;

public class Deck {
    private final List<Card> cards;

    public Deck(List<Card> cards) {
        // Validación de negocio: Un mazo legal debe tener 40 cartas
        if (cards == null || cards.size() != 40) {
            throw new IllegalArgumentException("Un mazo válido debe contener exactamente 40 cartas.");
        }
        this.cards = cards;
    }

    public List<Card> getCards() {
        return cards;
    }
}