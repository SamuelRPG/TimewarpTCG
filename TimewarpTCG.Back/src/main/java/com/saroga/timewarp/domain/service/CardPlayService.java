package com.saroga.timewarp.domain.service;

import com.saroga.timewarp.domain.model.entity.card.Card;
import com.saroga.timewarp.domain.model.entity.gameState.GameState;
import com.saroga.timewarp.domain.model.entity.playerState.PlayerState;
import com.saroga.timewarp.domain.model.enums.*;
import com.saroga.timewarp.domain.model.vo.card.TargetingRequirement;
import com.saroga.timewarp.domain.model.vo.gameState.QueuedSpell;

import java.util.List;
import java.util.UUID;

public class CardPlayService {

    public void executePlay(GameState gameState, UUID playerId, Card card, CardLocation destination, List<Card> targets) {
        PlayerState actor = gameState.getPlayerById(playerId);
        PlayerState opponent = gameState.getOpponentOf(playerId);
        boolean contested = gameState.isBattlefieldContested(destination);

        validatePlayCard(gameState, actor, card, destination, contested, targets);

        actor.executePlayCard(card, destination);

        if (card.isUnit()) {
            //disparar efectos de la carta al ser jugada
            card.onPlaced(actor, opponent, destination, targets);
            gameState.resetConsecutivePasses();
            gameState.switchPriority();
        }
        else if (card.isSpell()) {
            if (card.getSpellSpeed() == SpellSpeed.INSTANT) {
                // Los instant se aplican YA y NO van a la pila
                card.onPlaced(actor, opponent, destination, targets);
            } else {
                // Solo Fast y Slow van a la pila
                gameState.pushSpell(QueuedSpell.of(card, targets));

                if (card.getSpellSpeed() == SpellSpeed.FAST) {
                    if (gameState.getGamePhase() == GamePhase.MAIN_OPEN) { gameState.setGamePhase(GamePhase.MAIN_CLOSED); }
                    if (gameState.getGamePhase() == GamePhase.COMBAT_OPEN) { gameState.setGamePhase(GamePhase.COMBAT_CLOSED); }
                }
                else if (card.getSpellSpeed() == SpellSpeed.SLOW) {
                    if (gameState.getGamePhase() != GamePhase.MAIN_OPEN) throw new IllegalStateException("Slow solo en Main Open");
                    gameState.setGamePhase(GamePhase.MAIN_CLOSED);
                }
                gameState.resetConsecutivePasses();
                gameState.switchPriority();
            }
        }
    }

    public void validatePlayCard(GameState gameState, PlayerState actor, Card card, CardLocation destination, boolean isFieldContested, List<Card> targets) {
        if (card.getCost().value() > actor.getResource().value()) {
            throw new IllegalStateException("No tienes recurso para jugar esta carta");
        }
        if (card.getCardLocation() != CardLocation.HAND) {
            throw new IllegalStateException("No puedes jugar una carta que no esté en tu mano");
        }

        if (card.isUnit()) {
            validateUnitRequirements(gameState, card, destination, isFieldContested);
        } else if (card.isSpell()) {
            validateSpellRequirements(gameState, card, targets);
        }

        if (destination == CardLocation.GRAVEYARD ||destination == CardLocation.HAND || destination == CardLocation.DECK) {
            throw new IllegalStateException("Imposible jugar una carta aqui");
        }
    }

    private void validateUnitRequirements(GameState gameState, Card card, CardLocation destination, boolean isFieldContested) {
        validateTiming(card, gameState);

        PlayerState actor = gameState.getPlayerById(card.getOwnerId());

        if (actor.getUnitsIn(destination).size() >= 6) {
            throw new IllegalStateException("La zona de destino ya ha alcanzado el límite máximo de 6 unidades.");
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
    }

    private void validateSpellRequirements(GameState gameState, Card card, List<Card> targets) {
        validateTiming(card, gameState);
        validateTargets(card, targets);
    }

    private void validateTargets(Card card, List<Card> targets) {
        if (card.getTargetingRequirements().isEmpty() && !targets.isEmpty()) {
            throw new IllegalStateException("No puedes seleccionar unidades si el hechizo no tiene targets pendientes");
        }
        //este for filtra por cantidad segun grupo. Para cada grupo extrae la cantidad de targets que tiene
        //la carta en la lista de <TargetingRequirements> y lo compara
        for (TargetingRequirement req : card.getTargetingRequirements()) {
            long matchCount = targets.stream()
                    .filter(t -> matchesGroup(t, card.getOwnerId(), req.targetGroup()))
                    .count();

            if (matchCount != req.requiredCount()) {
                throw new IllegalStateException("Faltan o sobran objetivos de tipo " + req.targetGroup());
            }
        }

        int totalRequired = card.getTargetingRequirements().stream()
                .mapToInt(TargetingRequirement::requiredCount)
                .sum();

        if (targets.size() != totalRequired) {
            throw new IllegalStateException("La cantidad total de objetivos no coincide con lo que pide la carta.");
        }
    }

    private void validateTiming(Card card, GameState gameState) {
        SpellSpeed speed = card.getSpellSpeed();
        GamePhase phase = gameState.getGamePhase();

        // 1. REGLA PARA UNIDADES (Incluyendo las de Disruption)
        if (card.isUnit()) {
            if (phase == GamePhase.MAIN_CLOSED || phase == GamePhase.COMBAT_CLOSED) {
                throw new IllegalStateException("No puedes bajar unidades mientras se están lanzando hechizos.");
            }
            if (phase == GamePhase.COMBAT_OPEN) {
                throw new IllegalStateException("Las unidades no pueden entrar directamente en fase de combate.");
            }
        }

        if (card.isSpell()) {
            if (speed == SpellSpeed.SLOW && !gameState.isStackEmpty() && (phase != GamePhase.MAIN_OPEN)) {
                throw new IllegalStateException("Los hechizos SLOW solo se juegan en tu fase principal abierta.");
            }
            if (phase == GamePhase.MAIN_CLOSED || phase == GamePhase.COMBAT_CLOSED) {
                if (speed == SpellSpeed.SLOW) {
                    throw new IllegalStateException("No puedes responder con un hechizo SLOW.");
                }
            }
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
