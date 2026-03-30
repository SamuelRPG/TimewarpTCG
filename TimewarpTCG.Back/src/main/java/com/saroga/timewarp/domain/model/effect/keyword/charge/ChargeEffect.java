package com.saroga.timewarp.domain.model.effect.keyword.charge;

import com.saroga.timewarp.domain.model.effect.effectInterface.Effect;
import com.saroga.timewarp.domain.model.entity.card.Card;
import com.saroga.timewarp.domain.model.entity.playerState.PlayerState;
import com.saroga.timewarp.domain.model.enums.CardLocation;
import com.saroga.timewarp.domain.model.vo.card.Drive;

public class ChargeEffect implements Effect {
    private final int bonus = 1;

    @Override
    public void onMovementConfirmed(Card card, PlayerState owner, PlayerState opponent, CardLocation lane) {
        if (lane == CardLocation.FIELD_LEFT || lane == CardLocation.FIELD_RIGHT) {
            if (opponent.hasUnitsIn(lane) && owner.hasUnitsIn(lane)) {
                card.buffDrive(Drive.of(bonus));
            }
        }
    }
}
