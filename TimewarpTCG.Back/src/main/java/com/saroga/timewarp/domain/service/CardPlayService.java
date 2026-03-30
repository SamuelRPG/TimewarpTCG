package com.saroga.timewarp.domain.service;

import com.saroga.timewarp.domain.model.entity.card.Card;
import com.saroga.timewarp.domain.model.entity.gameState.GameState;
import com.saroga.timewarp.domain.model.entity.playerState.PlayerState;
import com.saroga.timewarp.domain.model.enums.CardLocation;
import com.saroga.timewarp.domain.model.enums.Keyword;

import java.util.List;
import java.util.UUID;

public class CardPlayService {

    public void executePlay(GameState gameState, UUID playerId, Card card, CardLocation destination, List<Card> targets) {
        PlayerState actor = gameState.getPlayerById(playerId);
        boolean contested = gameState.isBattlefieldContested(destination);

        validatePlayCard(actor, card, destination, contested);

        actor.executePlayCard(card, destination);

        //card.onPlaced

        gameState.switchPriority();
    }

    public void validatePlayCard(PlayerState actor, Card card, CardLocation destination, boolean isFieldContested) {
        if (card.getCost().value() > actor.getResource().value()) {
            throw new IllegalStateException("No tienes recurso para jugar esta carta");
        }
        if (card.getCardLocation() != CardLocation.HAND) {
            throw new IllegalStateException("No puedes jugar una carta que no esté en tu mano");
        }
        if (destination == CardLocation.FIELD_LEFT || destination == CardLocation.FIELD_RIGHT) {
            if (!card.hasKeyword(Keyword.DISRUPTION)) {
                throw new IllegalStateException("No puedes jugar una carta sin disruption en un battlefield");
            }
            if (card.hasKeyword(Keyword.DISRUPTION)) {
                if (!isFieldContested) {
                    throw new IllegalStateException("No se esta disputando el battlefield donde quieres jugar esta carta");
                }
            }
        }
        if (destination == CardLocation.GRAVEYARD ||destination == CardLocation.HAND || destination == CardLocation.DECK) {
            throw new IllegalStateException("Imposible jugar una carta aqui");
        }
    }
}
