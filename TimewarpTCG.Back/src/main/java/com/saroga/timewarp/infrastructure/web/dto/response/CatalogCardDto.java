package com.saroga.timewarp.infrastructure.web.dto.response;

import java.util.List;

/** Carta del catalogo (plantilla). Mismos nombres de campo que CardDto para que el cliente reutilice su modelo. */
public class CatalogCardDto {
    public String id;                 // id de plantilla, p. ej. "U_AE_01"
    public String name;
    public String description;
    public String cardType;           // UNIT | SPELL
    public int currentCost;
    public int currentDrive;
    public boolean isExhausted = false;
    public String faction;
    public List<String> keywords;
    public String spellSpeed;         // NOT_A_SPELL | SLOW | FAST | INSTANT
    public List<TargetReqDto> requirements;
}
