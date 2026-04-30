package com.saroga.timewarp.domain.service;

import com.saroga.timewarp.domain.model.entity.card.Card;
import com.saroga.timewarp.domain.model.entity.gameState.GameState;
import com.saroga.timewarp.domain.model.entity.playerState.PlayerState;
import com.saroga.timewarp.domain.model.enums.CardLocation;
import com.saroga.timewarp.domain.model.enums.ConsecutivePasses;
import com.saroga.timewarp.domain.model.enums.GamePhase;

import java.util.UUID;

public class GameFlowService {

    public void confirmMoveAndPassPriority(GameState gameState, UUID playerId, CombatService combatService) {
        PlayerState actor = gameState.getPlayerById(playerId);

        if (!actor.hasPriority()) {
            throw new IllegalStateException("No tienes la prioridad.");
        }

        boolean hasMovedUnits = !actor.getPendingMoves().isEmpty();
        boolean hasSpells = !gameState.isStackEmpty();

        if (hasMovedUnits) {
            resolvePendingMoves(gameState, actor);
            updatePhaseAfterMovement(gameState, combatService);
            actor.getPendingMoves().clear();
            gameState.resetConsecutivePasses();

            gameState.switchPriority();
        }
        else {
            gameState.addConsecutivePass();
            if (gameState.getConsecutivePasses() == ConsecutivePasses.TWO) {

                if (hasSpells) {
                    gameState.resolveQueue();
                    gameState.resetConsecutivePasses();
                    gameState.switchPriority();
                } else {
                    handlePhaseEnd(gameState, combatService);
                    gameState.resetConsecutivePasses();
                }
            } else {
                gameState.switchPriority();
            }
        }
    }

    private void handlePhaseEnd(GameState gameState, CombatService combatService) {
        GamePhase currentPhase = gameState.getGamePhase();

        if (currentPhase == GamePhase.COMBAT_OPEN || currentPhase == GamePhase.COMBAT_CLOSED) {
            checkAndResolveCombat(gameState, combatService);
        }
        else if (currentPhase == GamePhase.MAIN_OPEN || currentPhase == GamePhase.MAIN_CLOSED) {
            gameState.newTurn();
        }
    }

    private void checkAndResolveCombat(GameState gameState, CombatService combatService) {
        if (combatService.hasFieldUnitsFromBothPlayers(gameState.getPlayerA(), gameState.getPlayerB(), CardLocation.FIELD_LEFT)) {
            combatService.resolveLaneCombat(gameState, CardLocation.FIELD_LEFT);
        }

        if (combatService.hasFieldUnitsFromBothPlayers(gameState.getPlayerA(), gameState.getPlayerB(), CardLocation.FIELD_RIGHT)) {
            combatService.resolveLaneCombat(gameState, CardLocation.FIELD_RIGHT);
        }

        gameState.setGamePhase(GamePhase.MAIN_OPEN);
    }

    private void resolvePendingMoves(GameState gameState, PlayerState actor) {
        PlayerState opponent = gameState.getOpponentOf(actor.getPlayerId());

        for (Card card : actor.getPendingMoves()) {
            if (hasCardMovedThisRound(card)) {
                card.onMovementConfirmed(actor, opponent, card.getCardLocation());
                card.setCardLocationAtBeginningOfRound(card.getCardLocation());
                card.exhaust();
            }
        }
    }

    private void updatePhaseAfterMovement(GameState gameState, CombatService combatService) {
        if (gameState.getGamePhase() == GamePhase.COMBAT_OPEN ||
                gameState.getGamePhase() == GamePhase.COMBAT_CLOSED) return;

        boolean isContested =
                combatService.hasFieldUnitsFromBothPlayers(gameState.getPlayerA(), gameState.getPlayerB(), CardLocation.FIELD_LEFT) ||
                        combatService.hasFieldUnitsFromBothPlayers(gameState.getPlayerA(), gameState.getPlayerB(), CardLocation.FIELD_RIGHT);

        if (isContested) {
            gameState.setGamePhase(GamePhase.COMBAT_OPEN);
        }
    }

    private boolean hasCardMovedThisRound(Card card) {
        return card.getCardLocation() != card.getCardLocationAtBeginningOfRound();
    }
}
