package com.saroga.timewarp.domain.model.effect.triggered;

import com.saroga.timewarp.domain.model.effect.action.Action;
import com.saroga.timewarp.domain.model.effect.effectInterface.Effect;
import com.saroga.timewarp.domain.model.entity.card.Card;
import com.saroga.timewarp.domain.model.entity.gameState.GameState;
import com.saroga.timewarp.domain.model.entity.playerState.PlayerState;
import com.saroga.timewarp.domain.model.enums.CardLocation;
import com.saroga.timewarp.domain.model.vo.card.Drive;

import java.util.ArrayList;
import java.util.List;

public class TriggeredEffect implements Effect {
    private final String triggerEvent; // Ej: "ON_PLACED"
    private final Action action;       // Ej: AllyBoostAction

    public TriggeredEffect(String triggerEvent, Action action) {
        this.triggerEvent = triggerEvent;
        this.action = action;
    }

    @Override
    public void onDestroyed(GameState game,Card card) {
        if ("ON_DESTROYED".equals(triggerEvent)) {
            action.execute(game,card, new ArrayList<>());
        }
    }

    @Override
    public void onPlaced(GameState game,Card card, PlayerState owner, PlayerState opponent, CardLocation lane, List<Card> targets) {
        if ("ON_PLACED".equals(triggerEvent)) {
            action.execute(game, card, targets);
        }
    }

    @Override
    public boolean onPlacedWouldDoSomething(List<Card> targets) {
        return "ON_PLACED".equals(triggerEvent) && (!action.needsTargets() || (targets != null && !targets.isEmpty()));
    }

    @Override
    public void onDamageTaken(GameState game,Card card, Drive drive) {
        if ("ON_DAMAGE_TAKEN".equals(triggerEvent)) {
            action.execute(game, card, new ArrayList<>()); // No necesita targets externos
        }
    }

    @Override
    public void onCombatWin(GameState game,Card card, PlayerState owner, CardLocation lane) {
        if ("ON_COMBAT_WIN".equals(triggerEvent)) {
            action.execute(game, card, new ArrayList<>());
        }
    }

    @Override
    public void onMovementConfirmed(GameState game,Card card, PlayerState owner, PlayerState opponent, CardLocation lane) {
        if ("ON_MOVEMENT_CONFIRMED".equals(triggerEvent)) {
            action.execute(game, card, new ArrayList<>());
        }
    }

    // (Puedes añadir el resto de métodos de la interfaz Effect aquí de la misma forma)
}