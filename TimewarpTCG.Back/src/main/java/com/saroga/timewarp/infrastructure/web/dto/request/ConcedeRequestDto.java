package com.saroga.timewarp.infrastructure.web.dto.request;

import java.util.UUID;

/** Abandonar una partida en curso (el rival gana). */
public class ConcedeRequestDto {
    public UUID gameId;
    public UUID playerId;
}
