package com.saroga.timewarp.domain.model.entity.gameState;

import com.saroga.timewarp.domain.model.entity.playerState.PlayerState;
import com.saroga.timewarp.domain.model.entity.card.Card;
import com.saroga.timewarp.domain.model.vo.gameState.Turn;

import java.util.List;
import java.util.UUID;

public class GameState {
    private final UUID gameId;
    private UUID activePlayerId;
    private final PlayerState playerA;
    private final PlayerState playerB;
    private Turn turnNumber;
    private List<Card> allCardsInGame;

    public GameState(UUID id, PlayerState playerA, PlayerState playerB) {
        this.gameId = id;
        this.playerA = playerA;
        this.playerB = playerB;
        this.turnNumber = Turn.of(0);
        this.activePlayerId = playerA.getPlayerId();
    }
}
