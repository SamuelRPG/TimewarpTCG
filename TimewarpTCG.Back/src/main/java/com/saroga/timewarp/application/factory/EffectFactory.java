package com.saroga.timewarp.application.factory;

import com.saroga.timewarp.domain.model.effect.action.Action;
import com.saroga.timewarp.domain.model.effect.action.allyBoost.AllyBoostAction;
import com.saroga.timewarp.domain.model.effect.action.charge.ChargeAction;
import com.saroga.timewarp.domain.model.effect.action.enemyDamage.EnemyDamageAction;
import com.saroga.timewarp.domain.model.effect.action.readySelf.ReadySelfAction;
import com.saroga.timewarp.domain.model.effect.action.selfBuff.SelfBuffAction;
import com.saroga.timewarp.domain.model.effect.effectInterface.Effect;
import com.saroga.timewarp.domain.model.effect.triggered.TriggeredEffect;
import com.saroga.timewarp.domain.model.vo.card.Drive;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Component
public class EffectFactory {

    private final Map<String, Function<Integer, Action>> actionRegistry = new HashMap<>();

    public EffectFactory() {
        // Registramos el diccionario de acciones posibles
        actionRegistry.put("ENEMY_DAMAGE", value -> new EnemyDamageAction(Drive.of(value)));
        actionRegistry.put("ALLY_BOOST",   value -> new AllyBoostAction(Drive.of(value)));
        actionRegistry.put("SELF_BUFF",    value -> new SelfBuffAction(Drive.of(value)));
        actionRegistry.put("CHARGE_BUFF", value -> new ChargeAction(Drive.of(value)));
        actionRegistry.put("READY_SELF", value -> new ReadySelfAction());
    }

    public List<Effect> createEffects(String effectsRecipe) {
        List<Effect> effects = new ArrayList<>();

        if (effectsRecipe == null || effectsRecipe.isBlank() || effectsRecipe.equalsIgnoreCase("NONE")) {
            return effects;
        }

        // Dividimos múltiples efectos si los hubiera: "ON_PLACED:ENEMY_DAMAGE:2 ; ON_DAMAGE_TAKEN:SELF_BUFF:1"
        String[] rules = effectsRecipe.split(";");

        for (String rule : rules) {
            String[] parts = rule.split(":");

            if (parts.length < 2) continue;

            String triggerEvent = parts[0].trim().toUpperCase(); // Ej: "ON_PLACED"
            String actionName = parts[1].trim().toUpperCase();   // Ej: "ENEMY_DAMAGE"
            int value = parts.length > 2 ? Integer.parseInt(parts[2].trim()) : 0; // Ej: 2

            // Buscamos la acción en el diccionario
            Function<Integer, Action> constructor = actionRegistry.get(actionName);

            if (constructor != null) {
                Action theAction = constructor.apply(value);
                // Envolvemos la acción con su disparador
                effects.add(new TriggeredEffect(triggerEvent, theAction));
            } else {
                System.err.println("Efecto desconocido en BBDD: " + actionName);
            }
        }
        return effects;
    }
}