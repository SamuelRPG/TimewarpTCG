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
            card.onPlaced(gameState, actor, opponent, destination, targets);
            gameState.resetConsecutivePasses();
            gameState.switchPriority();
        }
        else if (card.isSpell()) {
            if (card.getSpellSpeed() == SpellSpeed.INSTANT) {
                card.onPlaced(gameState, actor, opponent, destination, targets);
                actor.getGraveyard().add(card);
                card.setCardLocation(CardLocation.GRAVEYARD);
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
        // unidades destruidas por el efecto recien jugado (ON_PLACED, hechizo INSTANT...) -> cementerio
        gameState.cleanupDestroyed();
    }

    public void validatePlayCard(GameState gameState, PlayerState actor, Card card, CardLocation destination, boolean isFieldContested, List<Card> targets) {
        if (!actor.getPendingMoves().isEmpty()) {
            throw new IllegalStateException("No puedes jugar cartas si ya has movido unidades. Confirma tus movimientos pasando la prioridad.");
        }
        if (card.getCost().value() > actor.getResource().value()) {
            throw new IllegalStateException("No tienes recurso para jugar esta carta");
        }
        if (card.getCardLocation() != CardLocation.HAND) {
            throw new IllegalStateException("No puedes jugar una carta que no esté en tu mano");
        }

        if (card.isUnit()) {
            validateUnitRequirements(gameState, card, destination, isFieldContested);
            validateUnitTargets(gameState, card, targets);
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
        validateTargetsAreUnitsInPlay(gameState, targets);
        validateTargets(card, targets);
    }

    /**
     * Objetivos de una UNIDAD (su efecto ON_PLACED). Es el servidor quien manda: un cliente modificado no puede
     * llevarse mas objetivos de los que dice la carta ni elegir cartas que no esten en juego.
     * Elegir MENOS objetivos de los que pide la carta se permite (el efecto simplemente no afecta a nadie): asi una
     * unidad se puede bajar aunque no haya objetivos validos.
     */
    private void validateUnitTargets(GameState gameState, Card card, List<Card> targets) {
        List<TargetingRequirement> reqs = card.getTargetingRequirements();
        if (reqs.isEmpty()) {
            if (!targets.isEmpty()) throw new IllegalStateException("Esta unidad no pide objetivos.");
            return;
        }
        validateTargetsAreUnitsInPlay(gameState, targets);
        int totalRequired = reqs.stream().mapToInt(TargetingRequirement::requiredCount).sum();
        if (targets.size() > totalRequired) {
            throw new IllegalStateException("Has elegido mas objetivos de los que pide la carta (" + totalRequired + ").");
        }
        // cada objetivo debe encajar en algun grupo que pide la carta...
        for (Card t : targets) {
            boolean fits = reqs.stream().anyMatch(r -> matchesGroup(t, card.getOwnerId(), r.targetGroup()));
            if (!fits) throw new IllegalStateException("Uno de los objetivos no es valido para esta carta.");
        }
        // ...y no puede haber mas aliados (o enemigos) de los que pide la carta
        int allowedAllies = reqs.stream().filter(r -> r.targetGroup() == TargetGroup.ALLY || r.targetGroup() == TargetGroup.ANY_UNIT)
                .mapToInt(TargetingRequirement::requiredCount).sum();
        int allowedEnemies = reqs.stream().filter(r -> r.targetGroup() == TargetGroup.ENEMY || r.targetGroup() == TargetGroup.ANY_UNIT)
                .mapToInt(TargetingRequirement::requiredCount).sum();
        long allies = targets.stream().filter(t -> t.getOwnerId().equals(card.getOwnerId())).count();
        long enemies = targets.size() - allies;
        if (allies > allowedAllies || enemies > allowedEnemies) {
            throw new IllegalStateException("Los objetivos elegidos no coinciden con los que pide la carta.");
        }
    }

    /** Un objetivo debe ser una unidad en juego (Base o carriles), viva y sin repetir: no vale una carta de la mano o del cementerio. */
    private void validateTargetsAreUnitsInPlay(GameState gameState, List<Card> targets) {
        java.util.Set<java.util.UUID> inPlay = new java.util.HashSet<>();
        gameState.getPlayerA().getAllCardsOnBoard().forEach(c -> inPlay.add(c.getId()));
        gameState.getPlayerB().getAllCardsOnBoard().forEach(c -> inPlay.add(c.getId()));
        java.util.Set<java.util.UUID> seen = new java.util.HashSet<>();
        for (Card t : targets) {
            if (t == null || !inPlay.contains(t.getId()) || t.isDestroyed()) {
                throw new IllegalStateException("Los objetivos deben ser unidades en juego.");
            }
            if (!seen.add(t.getId())) {
                throw new IllegalStateException("No puedes elegir la misma unidad dos veces.");
            }
        }
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

        if (card.isUnit()) {
            if (phase == GamePhase.MAIN_CLOSED || phase == GamePhase.COMBAT_CLOSED) {
                throw new IllegalStateException("No puedes bajar unidades mientras se están lanzando hechizos.");
            }
            if (phase == GamePhase.COMBAT_OPEN) {
                throw new IllegalStateException("Las unidades no pueden entrar directamente en fase de combate.");
            }
        }

        if (card.isSpell()) {
            // SLOW solo en MAIN_OPEN. (Antes solo se comprobaba con la pila NO vacia: en COMBAT_OPEN con la pila vacia
            // pasaba la validacion y el error saltaba despues de gastar el recurso y sacar la carta de la mano.)
            if (speed == SpellSpeed.SLOW && phase != GamePhase.MAIN_OPEN) {
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
