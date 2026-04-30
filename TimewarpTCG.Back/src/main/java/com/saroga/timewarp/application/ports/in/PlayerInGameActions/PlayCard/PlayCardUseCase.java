package com.saroga.timewarp.application.ports.in.PlayerInGameActions.PlayCard;

import com.saroga.timewarp.domain.model.entity.card.Card;
import com.saroga.timewarp.domain.model.entity.gameState.GameState;
import com.saroga.timewarp.domain.model.enums.CardLocation;
import java.util.List;
import java.util.UUID;

public interface PlayCardUseCase {
    void execute(GameState game, UUID playerId, UUID cardId, CardLocation destination, List<Card> targets);
}
