package com.saroga.timewarp.application.service.BotBrain;

import com.saroga.timewarp.application.ports.in.PlayerInGameActions.MoveCard.MoveCardUseCase;
import com.saroga.timewarp.application.ports.in.PlayerInGameActions.PassPriority.PassPriorityUseCase;
import com.saroga.timewarp.application.ports.in.PlayerInGameActions.PlayCard.PlayCardUseCase;
import com.saroga.timewarp.domain.model.entity.card.Card;
import com.saroga.timewarp.domain.model.entity.gameState.GameState;
import com.saroga.timewarp.domain.model.entity.playerState.PlayerState;
import com.saroga.timewarp.domain.model.enums.CardLocation;
import com.saroga.timewarp.domain.model.enums.GamePhase;
import com.saroga.timewarp.domain.model.enums.SpellSpeed;
import com.saroga.timewarp.domain.model.enums.TargetGroup;
import com.saroga.timewarp.domain.model.vo.card.TargetingRequirement;
import org.springframework.stereotype.Service;

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
        PlayerState bot = game.getPlayerA().isBot() ? game.getPlayerA() : game.getPlayerB();

        // Seguridad: Si no es el turno del bot o no tiene prioridad, ignoramos
        if (!bot.hasPriority()) return;

        try {
            decideAndAct(game, bot);
        } catch (IllegalStateException | IllegalArgumentException e) {
            // El bot NUNCA puede dejar la partida colgada: si su decision era ilegal, pasa prioridad.
            System.out.println("[BOT] Accion no valida (" + e.getMessage() + "). Paso prioridad.");
            passPriorityUseCase.execute(game, bot.getPlayerId());
        }
    }

    private void decideAndAct(GameState game, PlayerState bot) {
        PlayerState human = bot.getPlayerId().equals(game.getPlayerA().getPlayerId()) ? game.getPlayerB() : game.getPlayerA();

        if (!bot.getPendingMoves().isEmpty()) {
            passPriorityUseCase.execute(game, bot.getPlayerId());
            return;
        }

        // Fase CERRADA (hay algo en la pila): solo se puede responder con hechizos. El bot no lanza hechizos: espera pasando.
        if (game.getGamePhase() == GamePhase.MAIN_CLOSED || game.getGamePhase() == GamePhase.COMBAT_CLOSED) {
            passPriorityUseCase.execute(game, bot.getPlayerId());
            return;
        }

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

        Card spellToPlay = getPlayableSpell(game, bot);
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
        // Las unidades solo se pueden bajar en MAIN_OPEN (en COMBAT_* o MAIN_CLOSED el dominio lanza excepcion)
        Card cardToPlay = game.getGamePhase() == GamePhase.MAIN_OPEN ? getMostExpensivePlayableUnit(bot) : null;
        if (cardToPlay != null && bot.getBase().size() < 6) { // Recordamos el límite de 6 unidades
            // Unidades con "al entrar" que piden objetivo (daño, bonificación...): elegimos objetivos como con un hechizo
            List<Card> unitTargets = cardToPlay.getTargetingRequirements().isEmpty() ? null : getBestTargetsForSpell(game, bot, cardToPlay);
            playCardUseCase.execute(game, bot.getPlayerId(), cardToPlay.getId(), CardLocation.MY_BASE,
                    unitTargets != null ? unitTargets : new ArrayList<>());
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

    private Card getPlayableSpell(GameState game, PlayerState bot) {
        int currentMana = bot.getResource().value();
        // Buscamos el primer hechizo que podamos pagar (los SLOW solo se juegan en MAIN_OPEN)
        return bot.getHand().stream()
                .filter(c -> c.isSpell() && c.getCost().value() <= currentMana)
                .filter(c -> c.getSpellSpeed() != SpellSpeed.SLOW || game.getGamePhase() == GamePhase.MAIN_OPEN)
                .findFirst()
                .orElse(null);
    }

    private List<Card> getBestTargetsForSpell(GameState game, PlayerState bot, Card spell) {
        PlayerState human = game.getOpponentOf(bot.getPlayerId());
        List<Card> selected = new ArrayList<>();

        // Si el hechizo no requiere objetivos (ej. robar cartas) no hay nada que elegir
        for (TargetingRequirement req : spell.getTargetingRequirements()) {
            List<Card> candidates;
            switch (req.targetGroup()) {
                case ENEMY -> candidates = new ArrayList<>(human.getAllCardsOnBoard());
                case ALLY -> candidates = new ArrayList<>(bot.getAllCardsOnBoard());
                case ANY_UNIT -> {
                    candidates = new ArrayList<>(human.getAllCardsOnBoard());
                    candidates.addAll(bot.getAllCardsOnBoard());
                }
                default -> { return null; }   // GLOBAL / LANE: el dominio no los valida
            }
            candidates.removeAll(selected);
            candidates.removeIf(Card::isDestroyed);
            // los mas fuertes primero (para dañar al mejor enemigo o reforzar al mejor aliado)
            candidates.sort(Comparator.comparingInt((Card c) -> c.getDrive().value()).reversed());

            if (candidates.size() < req.requiredCount()) {
                return null;   // no hay objetivos suficientes: no lanzamos el hechizo
            }
            selected.addAll(candidates.subList(0, req.requiredCount()));
        }
        return selected;
    }
}
