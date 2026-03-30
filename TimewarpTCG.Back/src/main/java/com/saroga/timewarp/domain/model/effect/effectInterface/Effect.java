package com.saroga.timewarp.domain.model.effect.effectInterface;

import com.saroga.timewarp.domain.model.entity.card.Card;
import com.saroga.timewarp.domain.model.entity.playerState.PlayerState;
import com.saroga.timewarp.domain.model.enums.CardLocation;
import com.saroga.timewarp.domain.model.vo.card.Drive;

import java.util.List;

public interface Effect {
    default void onCombatWin(Card card, PlayerState owner, CardLocation lane) { }
    default void onDamageTaken(Card card, Drive drive) {}
    default void onPlaced(Card card, PlayerState owner, PlayerState opponent, CardLocation lane, List<Card> targets) {}
    default void onOtherUnitPlaced(Card card, PlayerState owner, CardLocation lane) {}
    default void onCombat(Card card, PlayerState owner, CardLocation lane) {}
    default int modifyDrive(Card card) {return 0;}
    default void beforeCombat(Card card) {}
    default void onMovementConfirmed(Card card, PlayerState owner, PlayerState opponent, CardLocation lane) {}
}
