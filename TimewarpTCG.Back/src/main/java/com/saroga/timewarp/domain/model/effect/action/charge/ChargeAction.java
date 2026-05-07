package com.saroga.timewarp.domain.model.effect.action.charge;

import com.saroga.timewarp.domain.model.effect.action.Action;
import com.saroga.timewarp.domain.model.entity.card.Card;
import com.saroga.timewarp.domain.model.enums.CardLocation;
import com.saroga.timewarp.domain.model.vo.card.Drive;
import java.util.List;

public class ChargeAction implements Action {
    private final Drive bonus;

    public ChargeAction(Drive bonus) {
        this.bonus = bonus;
    }

    @Override
    public void execute(Card source, List<Card> targets) {
        // En tu código original, comprobabas que estuviera en el FIELD disputado.
        // Como no le pasamos el 'lane' o el 'opponent' directamente a la acción por la interfaz simplificada,
        // podemos simplemente aplicar el buff, ya que el Trigger ON_MOVEMENT_CONFIRMED se encargará del cuándo.
        if (source.isAlive() && isField(source.getCardLocation())) {
            source.buffDrive(bonus);
            System.out.println(source.getName() + " carga a la batalla y gana +" + bonus.value());
        }
    }

    private boolean isField(CardLocation location) {
        return location == CardLocation.FIELD_LEFT || location == CardLocation.FIELD_RIGHT;
    }
}
