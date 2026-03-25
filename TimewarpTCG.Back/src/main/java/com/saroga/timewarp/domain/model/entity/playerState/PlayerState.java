package com.saroga.timewarp.domain.model.entity.playerState;

import com.saroga.timewarp.domain.model.entity.card.Card;
import com.saroga.timewarp.domain.model.enums.CardLocation;
import com.saroga.timewarp.domain.model.enums.Keyword;
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
        this.score = this.score.scorePoint();
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

    public void destroyCard(Card card) {
        card.destroy();
        this.graveyard.add(card);

        switch (card.getCardLocation()) {
            case FIELD_LEFT -> fieldLeft.remove(card);
            case FIELD_RIGHT -> fieldRight.remove(card);
            case MY_BASE -> base.remove(card);
        }
    }

    public boolean hasUnitsIn(CardLocation location) {
        List<Card> cards = getUnitsIn(location);
        return !cards.isEmpty();
    }

    public List<Card> getUnitsIn(CardLocation location) {
        return switch (location) {
            case FIELD_LEFT -> fieldLeft;
            case FIELD_RIGHT -> fieldRight;
            case MY_BASE -> base;
            case GRAVEYARD -> graveyard;
            case DECK -> deck;
            case HAND -> hand;
        };
    }

    public void moveCard(Card card, CardLocation destination) {
        validateMovement(card, destination);
        removeCardFromCurrentLocation(card);
        addCardToLocation(card, destination);

        card.setCardLocation(destination);
        card.exhaust();
    }

    private void removeCardFromCurrentLocation(Card card) {
        switch(card.getCardLocation()) {
            case FIELD_LEFT -> fieldLeft.remove(card);
            case FIELD_RIGHT -> fieldRight.remove(card);
            case MY_BASE -> base.remove(card);
        }
    }

    private void addCardToLocation(Card card, CardLocation destination) {
        switch (destination) {
            case FIELD_LEFT -> fieldLeft.add(card);
            case FIELD_RIGHT -> fieldRight.add(card);
            case MY_BASE -> base.add(card);
        }
    }

    private void validateMovement(Card card, CardLocation destination) {
        if (card.getCardLocation() == destination) {
            throw new IllegalStateException("Mueve la carta a un battlefield diferente");
        }
        if (card.getCardLocation() == CardLocation.GRAVEYARD || card.getCardLocation() == CardLocation.DECK ||card.getCardLocation() == CardLocation.HAND) {
            throw new IllegalStateException("No se pueden mover cartas que no están en juego");
        }
        if (destination == CardLocation.GRAVEYARD || destination == CardLocation.DECK || destination == CardLocation.HAND) {
            throw new IllegalStateException("No se pueden mover cartas que no están en juego");
        }
        if (card.isExhausted()) {
            throw new IllegalStateException("No puedes mover una carta exhausta");
        }
        if (isField(card.getCardLocation()) && isField(destination)) {
            if (!card.hasKeyword(Keyword.JUMPER)) {
                throw new IllegalStateException("No puedes mover unidades de un field a otro");
            }
        }
    }

    private boolean isField(CardLocation location) {
        return location == CardLocation.FIELD_LEFT || location == CardLocation.FIELD_RIGHT;
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
