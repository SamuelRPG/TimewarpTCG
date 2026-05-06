package com.saroga.timewarp.application.service.PlayerInGameActions.PassPriority;

import com.saroga.timewarp.application.ports.in.PlayerInGameActions.PassPriority.PassPriorityUseCase;
import com.saroga.timewarp.domain.model.entity.gameState.GameState;
import com.saroga.timewarp.domain.service.CombatService;
import com.saroga.timewarp.domain.service.GameFlowService;
import org.springframework.stereotype.Service;

import java.util.UUID;

public class PassPriorityService implements PassPriorityUseCase {
    private final GameFlowService gameFlowService;
    private final CombatService combatService;

    public PassPriorityService(GameFlowService gameFlowService, CombatService combatService) {
        this.gameFlowService = gameFlowService;
        this.combatService = combatService;
    }

    @Override
    public void execute(GameState game, UUID playerId) {
        gameFlowService.confirmMoveAndPassPriority(game, playerId, combatService);
    }
}