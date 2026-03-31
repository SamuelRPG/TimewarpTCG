package com.saroga.timewarp.domain.model.vo.gameState;

import com.saroga.timewarp.domain.model.entity.card.Card;

import java.util.List;

public record QueuedSpell(Card card, List<Card> targets) {

    public static QueuedSpell of(Card card, List<Card> targets) {
        return new QueuedSpell(card, targets);
    }

    @Override
    public Card card() {
        return card;
    }
    public List<Card> targets() {
        return targets;
    }
}
