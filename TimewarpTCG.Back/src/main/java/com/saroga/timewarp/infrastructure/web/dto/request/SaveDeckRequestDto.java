package com.saroga.timewarp.infrastructure.web.dto.request;

import com.saroga.timewarp.infrastructure.web.dto.response.DeckEntryDto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SaveDeckRequestDto {
    public UUID userId;
    public String deckName;
    // Mapa donde la Clave es el ID de la carta (Ej: "U_AE_01") y el Valor es cuántas copias hay (Ej: 3)
    public Map<String, Integer> cards;
    // Alternativa en lista (la usa el cliente Unity, porque su JsonUtility no serializa diccionarios)
    public List<DeckEntryDto> entries;
}
