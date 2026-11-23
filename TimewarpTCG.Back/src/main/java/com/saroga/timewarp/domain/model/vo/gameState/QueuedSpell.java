package com.saroga.timewarp.domain.model.vo.gameState;

import com.saroga.timewarp.domain.model.entity.card.Card;

import java.util.List;

/**
 * Una entrada de la pila. Normalmente es un HECHIZO (FAST/SLOW); si trigger = true es el efecto "al entrar en juego" de una UNIDAD
 * que ya esta sobre el tablero: se resuelve igual que un hechizo (el rival puede responder), pero la carta NO va al cementerio.
 */
public record QueuedSpell(Card card, List<Card> targets, boolean trigger) {
    public static QueuedSpell of(Card card, List<Card> targets) {
        return new QueuedSpell(card, targets, false);
    }

    public static QueuedSpell trigger(Card card, List<Card> targets) {
        return new QueuedSpell(card, targets, true);
    }

    @Override
    public Card card() {
        return card;
    }

    public List<Card> targets() {
        return targets;
    }
}
