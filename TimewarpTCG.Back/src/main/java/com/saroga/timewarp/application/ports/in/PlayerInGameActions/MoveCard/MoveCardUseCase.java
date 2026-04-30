package com.saroga.timewarp.application.ports.in.PlayerInGameActions.MoveCard;

import com.saroga.timewarp.domain.model.entity.gameState.GameState;
import com.saroga.timewarp.domain.model.enums.CardLocation;
import java.util.UUID;

public interface MoveCardUseCase {
    void execute(GameState game, UUID playerId, UUID cardId, CardLocation destination);
}
