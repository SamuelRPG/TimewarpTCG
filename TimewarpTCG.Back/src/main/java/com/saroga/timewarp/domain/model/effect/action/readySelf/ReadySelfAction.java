package com.saroga.timewarp.domain.model.effect.action.readySelf;

import com.saroga.timewarp.domain.model.effect.action.Action;
import com.saroga.timewarp.domain.model.entity.card.Card;
import com.saroga.timewarp.domain.model.entity.gameState.GameState;

import java.util.List;

public class ReadySelfAction implements Action {
    @Override
    public void execute(GameState game, Card source, List<Card> targets) {
        if (source.isAlive()) {
            source.ready();
            System.out.println(source.getName() + " se prepara para atacar de nuevo (Glory/Ready).");
        }
    }
}
