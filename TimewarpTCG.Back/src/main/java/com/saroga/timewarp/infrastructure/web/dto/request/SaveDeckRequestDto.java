package com.saroga.timewarp.infrastructure.web.dto.request;

import java.util.Map;
import java.util.UUID;

public class SaveDeckRequestDto {
    public UUID userId;
    public String deckName;
    // Mapa donde la Clave es el ID de la carta (Ej: "U_AE_01") y el Valor es cuántas copias hay (Ej: 3)
    public Map<String, Integer> cards;
}
