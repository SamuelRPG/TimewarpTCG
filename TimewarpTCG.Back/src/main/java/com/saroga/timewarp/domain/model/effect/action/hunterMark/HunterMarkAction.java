package com.saroga.timewarp.domain.model.effect.action.hunterMark;

import com.saroga.timewarp.domain.model.effect.action.Action;
import com.saroga.timewarp.domain.model.effect.action.specificCardBuff.SpecificCardBuffAction;
import com.saroga.timewarp.domain.model.effect.effectInterface.Effect;
import com.saroga.timewarp.domain.model.effect.triggered.TriggeredEffect;
import com.saroga.timewarp.domain.model.entity.card.Card;
import com.saroga.timewarp.domain.model.entity.gameState.GameState;
import com.saroga.timewarp.domain.model.vo.card.Drive;
import java.util.List;

public class HunterMarkAction implements Action {
    private final Drive damage;
    private final Drive buffReward;

    public HunterMarkAction(Drive damage, Drive buffReward) {
        this.damage = damage;
        this.buffReward = buffReward;
    }

    @Override
    public void execute(GameState game, Card hunter, List<Card> targets) {
        for (Card target : targets) {
            target.takeDamage(game, damage);

            if (target.isAlive()) {
                Action rewardAction = new SpecificCardBuffAction(hunter, buffReward);
                Effect markOfDeath = new TriggeredEffect("ON_DESTROYED", rewardAction);

                target.getEffects().add(markOfDeath);
                System.out.println(target.getName() + " ha sido marcado por " + hunter.getName());
            } else {
                // Si ha muerto del impacto directo, cobramos la recompensa ahora mismo
                hunter.buffDrive(buffReward);
                System.out.println("¡Muerte instantánea! " + hunter.getName() + " gana +" + buffReward.value());
            }
        }
    }

    @Override
    public boolean needsTargets() { return true; }
}
