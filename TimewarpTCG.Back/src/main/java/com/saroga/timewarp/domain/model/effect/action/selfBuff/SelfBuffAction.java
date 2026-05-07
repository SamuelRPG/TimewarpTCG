package com.saroga.timewarp.domain.model.effect.action.selfBuff;

import com.saroga.timewarp.domain.model.effect.action.Action;
import com.saroga.timewarp.domain.model.entity.card.Card;
import com.saroga.timewarp.domain.model.vo.card.Drive;
import java.util.List;

public class SelfBuffAction implements Action {
    private final Drive buffAmount;

    public SelfBuffAction(Drive buffAmount) {
        this.buffAmount = buffAmount;
    }

    @Override
    public void execute(Card source, List<Card> targets) {
        // Se buffa a sí misma, ignoramos los targets
        if (source.isAlive()) {
            source.buffDrive(buffAmount);
        }
    }
}