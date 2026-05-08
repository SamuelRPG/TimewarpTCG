package com.saroga.timewarp.domain.model.effect.action;

import com.saroga.timewarp.domain.model.entity.card.Card;
import com.saroga.timewarp.domain.model.entity.gameState.GameState;

import java.util.List;

public interface Action {
    // source = la carta que lanza el efecto. targets = a quién va dirigido
    void execute(GameState game, Card source, List<Card> targets);
}
