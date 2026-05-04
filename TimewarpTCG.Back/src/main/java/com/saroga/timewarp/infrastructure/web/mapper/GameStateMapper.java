package com.saroga.timewarp.infrastructure.web.mapper;

import com.saroga.timewarp.domain.model.entity.gameState.GameState;
import com.saroga.timewarp.domain.model.entity.playerState.PlayerState;
import com.saroga.timewarp.infrastructure.web.dto.response.GameStateDto;
import com.saroga.timewarp.infrastructure.web.dto.response.PlayerDto;

import java.util.UUID;

public class GameStateMapper {
    public static GameStateDto toGameStateDto(GameState game, UUID viewerId) {
        GameStateDto dto = new GameStateDto();
        dto.gameStateId = game.getGameId();
        dto.turn = game.getTurnNumber().value();
        dto.gamePhase = game.getGamePhase().name();

        PlayerState activePlayer = game.getPlayerById(viewerId);
        PlayerState opponent = game.getOpponentOf(viewerId);

        dto.activePlayer = PlayerMapper.toPlayerDto(activePlayer, true);
        dto.opponent = PlayerMapper.toPlayerDto(opponent, false);

        return dto;
    }
}
