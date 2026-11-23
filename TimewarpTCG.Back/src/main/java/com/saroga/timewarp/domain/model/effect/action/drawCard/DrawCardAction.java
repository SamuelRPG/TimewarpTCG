package com.saroga.timewarp.domain.model.effect.action.drawCard;

import com.saroga.timewarp.domain.model.effect.action.Action;
import com.saroga.timewarp.domain.model.entity.card.Card;
import com.saroga.timewarp.domain.model.entity.gameState.GameState;
import com.saroga.timewarp.domain.model.entity.playerState.PlayerState;

import java.util.List;
import java.util.UUID;

public class DrawCardAction implements Action {
    private final int amount;

    public DrawCardAction(int amount) {
        this.amount = amount;
    }

    //la lista de targets estara vacia en esta accion
    public void execute(GameState game,Card card, List<Card> targets) {
        PlayerState owner = game.getPlayerById(card.getOwnerId());
        for (int i = 0; i < amount; i++) {
            owner.drawCard();
        }
    }
}
