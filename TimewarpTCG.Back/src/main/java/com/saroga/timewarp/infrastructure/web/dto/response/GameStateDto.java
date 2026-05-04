package com.saroga.timewarp.infrastructure.web.dto.response;

import java.util.UUID;

public class GameStateDto {
    public UUID gameStateId;
    public int turn;
    public String gamePhase;
    public PlayerDto activePlayer;
    public PlayerDto opponent;
}
