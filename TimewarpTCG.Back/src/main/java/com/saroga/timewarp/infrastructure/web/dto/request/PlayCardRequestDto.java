package com.saroga.timewarp.infrastructure.web.dto.request;

import java.util.List;
import java.util.UUID;

public class PlayCardRequestDto {
    public UUID gameId;
    public UUID playerId;
    public UUID cardId;
    public String destination;
    public List<UUID> targetIds;
}
