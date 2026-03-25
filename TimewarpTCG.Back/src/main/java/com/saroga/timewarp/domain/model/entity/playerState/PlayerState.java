package com.saroga.timewarp.domain.model.entity.playerState;

import com.saroga.timewarp.domain.model.entity.card.Card;
import com.saroga.timewarp.domain.model.vo.playerState.Resource;
import com.saroga.timewarp.domain.model.vo.playerState.Score;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PlayerState {
    private final UUID playerId;
    private boolean priority;
    private Resource resource;
    private Score score;

    private List<Card> deck;
    private List<Card> hand;
    private List<Card> graveyard;
    private List<Card> base;
    private List<Card> fieldLeft;
    private List<Card> fieldRight;

    public PlayerState(UUID playerId, List<Card> deck, boolean hasPriority) {
        this.playerId = playerId;
        this.priority = hasPriority;
        this.resource = Resource.of(1);
        this.score = Score.of(0);

        this.deck = deck;
        this.hand = new ArrayList<>();
        this.graveyard = new ArrayList<>();
        this.base = new ArrayList<>();
        this.fieldLeft = new ArrayList<>();
        this.fieldRight = new ArrayList<>();
    }

    public boolean hasUnitsFieldLeft() {
        if (fieldLeft.isEmpty()) {
            return false;
        }
        return true;
    }

    public boolean hasUnitsFieldRight() {
        if (fieldRight.isEmpty()) {
            return false;
        }
        return true;
    }

    public void scorePoint() {
        this.score = Score.of(this.score.value() + 1);
    }

    public void drawCard() {
        if (deck.isEmpty()) {
            return;
        }

        hand.add(deck.getFirst());
        deck.removeFirst();
    }

    public void readyAllCards() {
        base.forEach(Card::ready);
        fieldRight.forEach(Card::ready);
        fieldLeft.forEach(Card::ready);
    }

    // getter y setter //

    public Score getPoints() {
        return score;
    }

    public void setPoints(Score points) {
        this.score = points;
    }

    public Resource getResource() {
        return resource;
    }

    public void setResource(Resource resource) {
        this.resource = resource;
    }

    public boolean hasPriority() {
        return priority;
    }

    public void setPriority(boolean priority) {
        this.priority = priority;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public boolean isPriority() {
        return priority;
    }

    public Score getScore() {
        return score;
    }

    public List<Card> getDeck() {
        return deck;
    }

    public List<Card> getHand() {
        return hand;
    }

    public List<Card> getGraveyard() {
        return graveyard;
    }

    public List<Card> getBase() {
        return base;
    }

    public List<Card> getFieldLeft() {
        return fieldLeft;
    }

    public List<Card> getFieldRight() {
        return fieldRight;
    }
}
