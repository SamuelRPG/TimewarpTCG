package com.saroga.timewarp.domain.model.effect.variable.enemyDamageDealt;

import com.saroga.timewarp.domain.model.effect.effectInterface.Effect;
import com.saroga.timewarp.domain.model.entity.card.Card;
import com.saroga.timewarp.domain.model.entity.playerState.PlayerState;
import com.saroga.timewarp.domain.model.enums.CardLocation;
import com.saroga.timewarp.domain.model.vo.card.Drive;

import java.util.List;

public class EnemyDamageDealtEffect implements Effect {
    private Drive amount;

    public EnemyDamageDealtEffect(Drive amount) {
        this.amount = amount;
    }

    @Override
    public void onPlaced(Card card, PlayerState owner, PlayerState opponent, CardLocation location, List<Card> targets) {
        for (Card target : targets) {
            if (target.getOwnerId().equals(opponent.getPlayerId())) {
                target.takeDamage(amount);
            }
        }
    }
}
