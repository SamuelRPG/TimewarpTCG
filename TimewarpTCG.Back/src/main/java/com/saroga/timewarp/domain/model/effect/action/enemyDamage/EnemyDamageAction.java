package com.saroga.timewarp.domain.model.effect.action.enemyDamage;

import com.saroga.timewarp.domain.model.effect.action.Action;
import com.saroga.timewarp.domain.model.entity.card.Card;
import com.saroga.timewarp.domain.model.entity.gameState.GameState;
import com.saroga.timewarp.domain.model.vo.card.Drive;
import java.util.List;

public class EnemyDamageAction implements Action {
    private final Drive amountDamage;

    public EnemyDamageAction(Drive amountDamage) {
        this.amountDamage = amountDamage;
    }

    @Override
    public void execute(GameState game, Card source, List<Card> targets) {
        for (Card target : targets) {
            if (!target.getOwnerId().equals(source.getOwnerId())) {
                target.takeDamage(game, amountDamage);
            }
        }
    }

    @Override
    public boolean needsTargets() { return true; }
}
