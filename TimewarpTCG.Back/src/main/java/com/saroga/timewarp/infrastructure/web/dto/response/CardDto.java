package com.saroga.timewarp.infrastructure.web.dto.response;

import java.util.UUID;

public class CardDto {
    public UUID id;
    public String name;
    public int currentDrive;
    public int currentCost;
    public boolean isExhausted;
}
