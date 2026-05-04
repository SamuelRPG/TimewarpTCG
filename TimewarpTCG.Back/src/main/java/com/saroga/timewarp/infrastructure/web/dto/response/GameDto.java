package com.saroga.timewarp.infrastructure.web.dto.response;

import java.util.UUID;

public class GameDto {
    private UUID gameStateId;
    private int turn;
    private String gamePhase;
    private PlayerDto activePlayer;
    private PlayerDto opponent;
}
