package com.saroga.timewarp.infrastructure.web.dto.response;

import java.util.List;
import java.util.UUID;

public class CardDto {
    public UUID id;
    public String name;
    public String description;
    public String cardType;
    public int currentDrive;
    public int currentCost;
    public boolean isExhausted;
    public String faction;
    public List<String> targetingRequirements;

    // --- Campos añadidos para el cliente Unity (aditivos: el JSON anterior sigue siendo valido) ---
    public List<String> keywords;                 // DISRUPTION, GLORY, JUMPER, CHARGE
    public String spellSpeed;                     // NOT_A_SPELL, SLOW, FAST, INSTANT
    public List<TargetReqDto> requirements;       // version estructurada de targetingRequirements
}
