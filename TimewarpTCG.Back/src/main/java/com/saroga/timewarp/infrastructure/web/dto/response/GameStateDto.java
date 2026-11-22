package com.saroga.timewarp.infrastructure.web.dto.response;

import java.util.List;
import java.util.UUID;

public class GameStateDto {
    public UUID gameStateId;
    public int turn;
    public String gamePhase;
    public PlayerDto activePlayer;
    public PlayerDto opponent;

    // --- Campos añadidos para el cliente Unity ---
    public String gameStatus;                     // ONGOING | FINISHED
    public UUID winnerId;                         // null mientras la partida sigue
    public String consecutivePasses;              // ZERO | ONE | TWO
    public List<StackEntryDto> spellStack;        // hechizos pendientes de resolver
}
