package com.saroga.timewarp.domain.model.effect.variable.allyBoost;

import com.saroga.timewarp.domain.model.effect.effectInterface.Effect;
import com.saroga.timewarp.domain.model.entity.card.Card;
import com.saroga.timewarp.domain.model.entity.playerState.PlayerState;
import com.saroga.timewarp.domain.model.enums.CardLocation;
import com.saroga.timewarp.domain.model.vo.card.Drive;

import java.util.List;

public class AllyBoostEffect implements Effect {

    private Drive amountBoost;

    public AllyBoostEffect(Drive amountBoost) {
        this.amountBoost = amountBoost;
    }
    @Override
    public void onPlaced(Card card, PlayerState owner, PlayerState opponent, CardLocation location, List<Card> targets) {
        for (Card target : targets) {
            if (target.getOwnerId().equals(owner.getPlayerId())) {
                target.buffDrive(amountBoost);
            }
        }
    }
}
