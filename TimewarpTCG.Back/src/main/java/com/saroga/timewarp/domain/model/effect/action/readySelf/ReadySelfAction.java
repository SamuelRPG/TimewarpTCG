package com.saroga.timewarp.domain.model.effect.action.readySelf;

import com.saroga.timewarp.domain.model.effect.action.Action;
import com.saroga.timewarp.domain.model.entity.card.Card;
import java.util.List;

public class ReadySelfAction implements Action {
    @Override
    public void execute(Card source, List<Card> targets) {
        if (source.isAlive()) {
            source.ready();
            System.out.println(source.getName() + " se prepara para atacar de nuevo (Glory/Ready).");
        }
    }
}
