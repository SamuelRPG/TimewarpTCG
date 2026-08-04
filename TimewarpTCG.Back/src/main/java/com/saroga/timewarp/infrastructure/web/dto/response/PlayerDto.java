package com.saroga.timewarp.infrastructure.web.dto.response;

import com.saroga.timewarp.domain.model.vo.playerState.Resource;
import com.saroga.timewarp.domain.model.vo.playerState.Score;

import java.util.List;
import java.util.UUID;

public class PlayerDto {
    public UUID id;
    public int score;
    public int resource;
    public boolean hasPriority;

    // Tamaños ocultos
    public int deckSize;
    public int graveyardSize;
    public int handSize;

    // Listas visibles
    public List<CardDto> hand; // Será null para el enemigo
    public List<CardDto> baseZone;
    public List<CardDto> fieldLeft;
    public List<CardDto> fieldRight;
}
