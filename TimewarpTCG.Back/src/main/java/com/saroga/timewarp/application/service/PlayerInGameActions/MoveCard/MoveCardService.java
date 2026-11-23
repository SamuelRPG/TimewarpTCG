package com.saroga.timewarp.application.service.PlayerInGameActions.MoveCard;


import com.saroga.timewarp.application.ports.in.PlayerInGameActions.MoveCard.MoveCardUseCase;
import com.saroga.timewarp.domain.model.entity.card.Card;
import com.saroga.timewarp.domain.model.entity.gameState.GameState;
import com.saroga.timewarp.domain.model.enums.GamePhase;
import com.saroga.timewarp.domain.model.entity.playerState.PlayerState;
import com.saroga.timewarp.domain.model.enums.CardLocation;
import com.saroga.timewarp.domain.service.CombatService;
import com.saroga.timewarp.domain.service.GameFlowService;
import org.springframework.stereotype.Service;

import java.util.UUID;

public class MoveCardService implements MoveCardUseCase {
    private final GameFlowService gameFlowService;
    private final CombatService combatService;

    public MoveCardService(GameFlowService gameFlowService, CombatService combatService) {
        this.gameFlowService = gameFlowService;
        this.combatService = combatService;
    }

    @Override
    public void execute(GameState game, UUID playerId, UUID cardId, CardLocation destination) {
        PlayerState actor = game.getPlayerById(playerId);

        if (!actor.hasPriority()) {
            throw new IllegalStateException("No puedes mover cartas si no es tu turno.");
        }

        // Con algo en la pila la fase esta CERRADA: solo se puede responder con hechizos FAST/INSTANT o pasar. Antes no se comprobaba
        // y el rival podia mover (y confirmar) unidades mientras un hechizo esperaba a resolverse.
        GamePhase phase = game.getGamePhase();
        if (phase == GamePhase.MAIN_CLOSED || phase == GamePhase.COMBAT_CLOSED || !game.isStackEmpty()) {
            throw new IllegalStateException("No puedes mover unidades mientras hay algo en la pila: solo puedes responder con un hechizo FAST o INSTANT, o pasar.");
        }
        Card cardToMove = actor.getAllCardsOnBoard().stream()
                .filter(c -> c.getId().equals(cardId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("La carta no está en el tablero"));

        actor.moveCard(cardToMove, destination);
    }

}
