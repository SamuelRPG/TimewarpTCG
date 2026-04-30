package com.saroga.timewarp.application.service.BotBrain;

import com.saroga.timewarp.application.ports.in.PlayerInGameActions.MoveCard.MoveCardUseCase;
import com.saroga.timewarp.application.ports.in.PlayerInGameActions.PassPriority.PassPriorityUseCase;
import com.saroga.timewarp.application.ports.in.PlayerInGameActions.PlayCard.PlayCardUseCase;
import com.saroga.timewarp.domain.model.entity.card.Card;
import com.saroga.timewarp.domain.model.entity.gameState.GameState;
import com.saroga.timewarp.domain.model.entity.playerState.PlayerState;
import com.saroga.timewarp.domain.model.enums.CardLocation;
import com.saroga.timewarp.domain.model.enums.TargetGroup;
import com.saroga.timewarp.domain.model.vo.card.TargetingRequirement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class BotBrainService {

    private final PlayCardUseCase playCardUseCase;
    private final MoveCardUseCase moveCardUseCase;
    private final PassPriorityUseCase passPriorityUseCase;

    public BotBrainService(PlayCardUseCase playCardUseCase, MoveCardUseCase moveCardUseCase, PassPriorityUseCase passPriorityUseCase) {
        this.playCardUseCase = playCardUseCase;
        this.moveCardUseCase = moveCardUseCase;
        this.passPriorityUseCase = passPriorityUseCase;
    }

    public void takeTurn(GameState game) {
        // 1. Obtenemos al Bot (Para el MVP asumimos que siempre es el Jugador B)
        PlayerState bot = game.getPlayerB();
        PlayerState human = game.getPlayerA();

        // Seguridad: Si no es el turno del bot o no tiene prioridad, ignoramos
        if (!bot.hasPriority()) return;

        // --- REGLA 1: DEFENSA DE EMERGENCIA ---
        // Si el humano tiene tropas en FIELD_LEFT y yo no tengo a nadie defendiendo ahí
        if (human.hasUnitsIn(CardLocation.FIELD_LEFT) && !bot.hasUnitsIn(CardLocation.FIELD_LEFT)) {
            Card bestDefender = getBestReadyUnit(bot);
            if (bestDefender != null) {
                // Sube a defender y avisa de que ha terminado de pensar
                moveCardUseCase.execute(game, bot.getPlayerId(), bestDefender.getId(), CardLocation.FIELD_LEFT);
                return;
            }
        }

        // Lo mismo para el FIELD_RIGHT
        if (human.hasUnitsIn(CardLocation.FIELD_RIGHT) && !bot.hasUnitsIn(CardLocation.FIELD_RIGHT)) {
            Card bestDefender = getBestReadyUnit(bot);
            if (bestDefender != null) {
                moveCardUseCase.execute(game, bot.getPlayerId(), bestDefender.getId(), CardLocation.FIELD_RIGHT);
                return;
            }
        }

        // --- REGLA 2: ATAQUE OPORTUNISTA ---
        // Si el FIELD_LEFT está vacío (ni mío ni suyo), mando tropas a ganar puntos
        if (!bot.hasUnitsIn(CardLocation.FIELD_LEFT) && !human.hasUnitsIn(CardLocation.FIELD_LEFT)) {
            Card attacker = getBestReadyUnit(bot);
            if (attacker != null) {
                moveCardUseCase.execute(game, bot.getPlayerId(), attacker.getId(), CardLocation.FIELD_LEFT);
                return;
            }
        }

        Card spellToPlay = getPlayableSpell(bot);
        if (spellToPlay != null) {
            List<Card> targets = getBestTargetsForSpell(game, bot, spellToPlay);

            // Si el método nos devuelve una lista válida (no nula), ¡disparamos!
            if (targets != null) {
                // El destino (CardLocation.MY_BASE) suele ser ignorado por los hechizos globales,
                // pero se lo pasamos por cumplir con el Caso de Uso.
                playCardUseCase.execute(game, bot.getPlayerId(), spellToPlay.getId(), CardLocation.MY_BASE, targets);
                return;
            }
        }

        // --- REGLA 3: DESARROLLO (Bajar cartas) ---
        // Si no hay urgencias en el tablero, miro si tengo maná para bajar la carta más fuerte de mi mano
        Card cardToPlay = getMostExpensivePlayableUnit(bot);
        if (cardToPlay != null && bot.getBase().size() < 6) { // Recordamos el límite de 6 unidades
            playCardUseCase.execute(game, bot.getPlayerId(), cardToPlay.getId(), CardLocation.MY_BASE, new ArrayList<>());
            return;
        }

        // --- REGLA 4: PASAR TURNO ---
        // Si ya he defendido, atacado, bajado cartas, o simplemente no tengo recurso... paso prioridad
        passPriorityUseCase.execute(game, bot.getPlayerId());
    }

    // --- MÉTODOS "OJOS DEL BOT" (Análisis del entorno) ---

    private Card getMostExpensivePlayableUnit(PlayerState bot) {
        int currentMana = bot.getResource().value();
        Card bestCard = null;

        for (Card card : bot.getHand()) {
            // MVP: El bot solo jugará Unidades para no liarse con targets de Hechizos
            if (card.isUnit() && card.getCost().value() <= currentMana) {
                if (bestCard == null || card.getCost().value() > bestCard.getCost().value()) {
                    bestCard = card;
                }
            }
        }
        return bestCard;
    }

    private Card getBestReadyUnit(PlayerState bot) {
        Card bestUnit = null;
        for (Card card : bot.getBase()) {
            if (!card.isExhausted()) {
                // Cogemos la unidad que tenga más Drive
                if (bestUnit == null || card.getDrive().value() > bestUnit.getDrive().value()) {
                    bestUnit = card;
                }
            }
        }
        return bestUnit;
    }

    // --- NUEVOS MÉTODOS "OJOS DEL BOT" ---

    private Card getPlayableSpell(PlayerState bot) {
        int currentMana = bot.getResource().value();
        // Buscamos el primer hechizo que podamos pagar
        return bot.getHand().stream()
                .filter(c -> c.isSpell() && c.getCost().value() <= currentMana)
                .findFirst()
                .orElse(null);
    }

    private List<Card> getBestTargetsForSpell(GameState game, PlayerState bot, Card spell) {
        PlayerState human = game.getPlayerA();
        List<Card> selectedTargets = new ArrayList<>();

        // Si el hechizo no requiere objetivos (ej. robar cartas, ganar maná global)
        if (spell.getTargetingRequirements().isEmpty()) {
            return selectedTargets;
        }

        // Analizamos qué pide el hechizo
        for (TargetingRequirement req : spell.getTargetingRequirements()) {

            // Si requiere apuntar a un ENEMIGO
            if (req.targetGroup() == TargetGroup.ENEMY) {
                // Buscamos la carta del humano con más vida en la mesa para destruirla
                Card bestEnemy = human.getAllCardsOnBoard().stream()
                        .max(Comparator.comparingInt(c -> c.getDrive().value()))
                        .orElse(null);

                if (bestEnemy != null) {
                    selectedTargets.add(bestEnemy);
                } else {
                    return null; // No hay enemigos, el hechizo se cancela (no lo lanzamos)
                }
            }
            // Si requiere apuntar a un ALIADO (Un buff de fuerza, por ejemplo)
            else if (req.targetGroup() == TargetGroup.ALLY) {
                // Buscamos a nuestro aliado con más vida para hacerlo aún más fuerte
                Card bestAlly = bot.getAllCardsOnBoard().stream()
                        .max(Comparator.comparingInt(c -> c.getDrive().value()))
                        .orElse(null);

                if (bestAlly != null) {
                    selectedTargets.add(bestAlly);
                } else {
                    return null; // No hay aliados en mesa, no desperdiciamos el hechizo
                }
            }
        }

        // Comprobamos si hemos reunido todos los objetivos necesarios
        int totalRequired = spell.getTargetingRequirements().stream()
                .mapToInt(TargetingRequirement::requiredCount)
                .sum();

        if (selectedTargets.size() == totalRequired) {
            return selectedTargets;
        }

        return null; // Falló la selección
    }
}
