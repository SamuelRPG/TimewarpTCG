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
}
