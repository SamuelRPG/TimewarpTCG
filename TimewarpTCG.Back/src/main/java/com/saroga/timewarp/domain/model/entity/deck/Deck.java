package com.saroga.timewarp.domain.model.entity.deck;

import com.saroga.timewarp.domain.model.entity.card.Card;
import com.saroga.timewarp.domain.model.entity.cardTemplate.CardTemplate;

import java.util.List;

public class Deck {
    private final List<CardTemplate> cards;

    public Deck(List<CardTemplate> cards) {
        // Validación de negocio: Un mazo legal debe tener 40 cartas
        if (cards == null || cards.size() != 40) {
            throw new IllegalArgumentException("Un mazo válido debe contener exactamente 40 cartas.");
        }
        this.cards = cards;
    }

    public List<CardTemplate> getTemplates() {
        return cards;
    }
}