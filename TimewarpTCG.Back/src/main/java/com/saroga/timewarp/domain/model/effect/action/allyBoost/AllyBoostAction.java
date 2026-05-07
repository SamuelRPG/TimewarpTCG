package com.saroga.timewarp.domain.model.effect.action.allyBoost;

import com.saroga.timewarp.domain.model.effect.action.Action;
import com.saroga.timewarp.domain.model.entity.card.Card;
import com.saroga.timewarp.domain.model.vo.card.Drive;
import java.util.List;

public class AllyBoostAction implements Action {
    private final Drive amountBoost;

    public AllyBoostAction(Drive amountBoost) {
        this.amountBoost = amountBoost;
    }

    @Override
    public void execute(Card source, List<Card> targets) {
        for (Card target : targets) {
            if (target.getOwnerId().equals(source.getOwnerId())) {
                target.buffDrive(amountBoost);
            }
        }
    }
}
