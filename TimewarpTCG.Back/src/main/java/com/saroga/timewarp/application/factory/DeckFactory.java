package com.saroga.timewarp.application.factory;

import com.saroga.timewarp.domain.model.entity.card.Card;
import com.saroga.timewarp.domain.model.entity.deck.Deck;
import com.saroga.timewarp.domain.model.vo.card.Cost;
import com.saroga.timewarp.domain.model.vo.card.Drive;
import com.saroga.timewarp.domain.model.enums.SpellSpeed;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DeckFactory {

    // MAZO 1: Mazo Agresivo (Unidades rápidas y baratas)
    public Deck createAggroDeck(UUID ownerId) {
        List<Card> cards = new ArrayList<>();

        // Metemos 25 Guerreros Básicos
        for (int i = 0; i < 25; i++) {
            cards.add(Card.createUnit(ownerId, "Infantería Rápida", Drive.of(2), Cost.of(1),
                    new ArrayList<>(), new ArrayList<>(), new ArrayList<>()));
        }

        // Metemos 15 Tanques
        for (int i = 0; i < 15; i++) {
            cards.add(Card.createUnit(ownerId, "Vanguardia Pesada", Drive.of(5), Cost.of(3),
                    new ArrayList<>(), new ArrayList<>(), new ArrayList<>()));
        }

        return new Deck(cards);
    }

    // MAZO 2: Mazo de Control (Hechizos y unidades defensivas)
    public Deck createControlDeck(UUID ownerId) {
        List<Card> cards = new ArrayList<>();

        // 20 Muros Defensivos
        for (int i = 0; i < 20; i++) {
            cards.add(Card.createUnit(ownerId, "Muro de Energía", Drive.of(4), Cost.of(2),
                    new ArrayList<>(), new ArrayList<>(), new ArrayList<>()));
        }

        // 20 Hechizos básicos (sin efectos reales aún para simplificar la creación)
        for (int i = 0; i < 20; i++) {
            cards.add(Card.createSpell(ownerId, "Chispa Temporal", Cost.of(1),
                    new ArrayList<>(), new ArrayList<>(), SpellSpeed.FAST));
        }

        return new Deck(cards);
    }

    // Método auxiliar para que el Caso de Uso elija por nombre
    public Deck getDeckByName(String deckName, UUID ownerId) {
        if ("AGGRO".equalsIgnoreCase(deckName)) {
            return createAggroDeck(ownerId);
        } else if ("CONTROL".equalsIgnoreCase(deckName)) {
            return createControlDeck(ownerId);
        } else {
            // Mazo por defecto si falla el nombre
            return createAggroDeck(ownerId);
        }
    }
}