package com.saroga.timewarp.domain.model.effect.action.specificCardBuff;

import com.saroga.timewarp.domain.model.effect.action.Action;
import com.saroga.timewarp.domain.model.entity.card.Card;
import com.saroga.timewarp.domain.model.entity.gameState.GameState;
import com.saroga.timewarp.domain.model.vo.card.Drive;
import java.util.List;

public class SpecificCardBuffAction implements Action {
    private final Card beneficiary; // Quien recibe el buff (El cazador)
    private final Drive buffAmount;

    public SpecificCardBuffAction(Card beneficiary, Drive buffAmount) {
        this.beneficiary = beneficiary;
        this.buffAmount = buffAmount;
    }

    @Override
    public void execute(GameState game, Card dyingEnemy, List<Card> targets) {
        // Ignoramos al dyingEnemy y buffamos directamente a nuestro beneficiario
        if (beneficiary.isAlive()) {
            beneficiary.buffDrive(buffAmount);
            System.out.println("¡La marca se ha cobrado! " + beneficiary.getName() + " gana +" + buffAmount.value());
        }
    }
}