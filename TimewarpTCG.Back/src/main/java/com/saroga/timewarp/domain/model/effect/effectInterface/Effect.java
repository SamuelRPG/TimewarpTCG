package com.saroga.timewarp.domain.model.effect.effectInterface;

import com.saroga.timewarp.domain.model.entity.card.Card;
import com.saroga.timewarp.domain.model.entity.gameState.GameState;
import com.saroga.timewarp.domain.model.entity.playerState.PlayerState;
import com.saroga.timewarp.domain.model.enums.CardLocation;
import com.saroga.timewarp.domain.model.vo.card.Drive;

import java.util.List;

public interface Effect {
    default void onCombatWin(GameState game, Card card, PlayerState owner, CardLocation lane) { }
    default void onDamageTaken(GameState game,Card card, Drive drive) {}
    default void onPlaced(GameState game, Card card, PlayerState owner, PlayerState opponent, CardLocation lane, List<Card> targets) {}
    default void onOtherUnitPlaced(GameState game,Card card, PlayerState owner, CardLocation lane) {}
    default void onCombat(GameState game,Card card, PlayerState owner, CardLocation lane) {}
    default int modifyDrive(GameState game,Card card) {return 0;}
    default void beforeCombat(GameState game,Card card) {}
    default void onMovementConfirmed(GameState game,Card card, PlayerState owner, PlayerState opponent, CardLocation lane) {}
    default void onDestroyed(GameState game,Card card) {}

    /**
     * ¿Este efecto hace algo al entrar la carta en juego (ON_PLACED) con estos objetivos? Sirve para decidir si el efecto "al entrar"
     * se pone en la PILA: si no haria nada (necesita objetivos y no hay), no se abre una pila inutil.
     */
    default boolean onPlacedWouldDoSomething(List<Card> targets) { return false; }
}
