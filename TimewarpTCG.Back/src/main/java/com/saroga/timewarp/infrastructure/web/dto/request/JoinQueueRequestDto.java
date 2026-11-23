package com.saroga.timewarp.infrastructure.web.dto.request;

import java.util.UUID;

/** Buscar partida contra otra persona con el mazo indicado (AGGRO, CONTROL o uno guardado en tu cuenta). */
public class JoinQueueRequestDto {
    public UUID playerId;
    public String deckName;
}
