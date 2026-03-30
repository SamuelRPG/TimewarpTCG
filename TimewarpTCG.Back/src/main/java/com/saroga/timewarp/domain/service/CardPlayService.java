package com.saroga.timewarp.domain.service;

import com.saroga.timewarp.domain.model.entity.card.Card;
import com.saroga.timewarp.domain.model.entity.gameState.GameState;
import com.saroga.timewarp.domain.model.entity.playerState.PlayerState;
import com.saroga.timewarp.domain.model.enums.CardLocation;
import com.saroga.timewarp.domain.model.enums.Keyword;
import com.saroga.timewarp.domain.model.enums.TargetGroup;
import com.saroga.timewarp.domain.model.vo.card.TargetingRequirement;

import java.util.List;
import java.util.UUID;

public class CardPlayService {

    public void executePlay(GameState gameState, UUID playerId, Card card, CardLocation destination, List<Card> targets) {
        PlayerState actor = gameState.getPlayerById(playerId);
        PlayerState opponent = gameState.getOpponentOf(playerId);
        boolean contested = gameState.isBattlefieldContested(destination);

        validatePlayCard(actor, card, destination, contested, targets);

        actor.executePlayCard(card, destination);

        gameState.switchPriority();
    }

    public void validatePlayCard(PlayerState actor, Card card, CardLocation destination, boolean isFieldContested, List<Card> targets) {
        if (card.getCost().value() > actor.getResource().value()) {
            throw new IllegalStateException("No tienes recurso para jugar esta carta");
        }
        if (card.getCardLocation() != CardLocation.HAND) {
            throw new IllegalStateException("No puedes jugar una carta que no esté en tu mano");
        }

        if (card.isUnit()) {
            validateUnitRequirements(card, destination, isFieldContested);
        } else if (card.isSpell()) {

        }

        if (destination == CardLocation.GRAVEYARD ||destination == CardLocation.HAND || destination == CardLocation.DECK) {
            throw new IllegalStateException("Imposible jugar una carta aqui");
        }
    }

    private void validateUnitRequirements(Card card, CardLocation destination, boolean isFieldContested) {
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
    }

    private void validateTargets(Card card, List<Card> targets) {
        if (card.getTargetingRequirements().isEmpty() && !targets.isEmpty()) {
            throw new IllegalStateException("No puedes seleccionar unidades si el hechizo no tiene targets pendientes");
        }

        for (TargetingRequirement requirement : card.getTargetingRequirements()) {

        }
    }

//    Su función exacta es determinar si una carta específica que el jugador ha seleccionado
//    (el target) cumple con el bando que exige el hechizo, basándose en quién es el jugador que lo está lanzando (ownerId).
    private boolean matchesGroup(Card target, UUID ownerId, TargetGroup group) {
        return switch (group) {
            case ALLY -> target.getOwnerId().equals(ownerId);
            case ENEMY -> !target.getOwnerId().equals(ownerId);
            case ANY_UNIT -> true; // Vale cualquier cosa que esté en el tablero
            default -> false;
        };
}
}
