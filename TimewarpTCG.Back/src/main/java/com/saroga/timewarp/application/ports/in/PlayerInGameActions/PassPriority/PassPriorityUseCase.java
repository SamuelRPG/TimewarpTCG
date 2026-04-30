package com.saroga.timewarp.application.ports.in.PlayerInGameActions.PassPriority;

import com.saroga.timewarp.domain.model.entity.gameState.GameState;
import java.util.UUID;

public interface PassPriorityUseCase {
    void execute(GameState game, UUID playerId);
}
