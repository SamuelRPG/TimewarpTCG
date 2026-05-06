package com.saroga.timewarp.infrastructure.web.dto.request;

import java.util.UUID;

public class MoveCardRequestDto {
    public UUID gameId;
    public UUID playerId;
    public UUID cardId;
    public String destination;
}
