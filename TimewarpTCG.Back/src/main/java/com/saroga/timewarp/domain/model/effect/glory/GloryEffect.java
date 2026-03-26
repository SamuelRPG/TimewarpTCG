package com.saroga.timewarp.domain.model.effect.glory;

import com.saroga.timewarp.domain.model.effect.effectInterface.Effect;
import com.saroga.timewarp.domain.model.entity.card.Card;
import com.saroga.timewarp.domain.model.entity.playerState.PlayerState;
import com.saroga.timewarp.domain.model.enums.CardLocation;

public class GloryEffect implements Effect {
    @Override
    public void onCombatWin(Card card, PlayerState owner, CardLocation lane) {
        card.ready();
    }
}
