package com.saroga.timewarp.infrastructure.web.dto.request;


import java.util.UUID;

public class StartMatchRequestDto {
    public UUID playerId;
    public String deckName;
    // Mazo del bot: "RANDOM" (por defecto si falta o va vacio), "AGGRO" o "CONTROL"
    public String botDeck;
}
