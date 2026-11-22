package com.saroga.timewarp.infrastructure.web.dto.response;

import java.util.List;
import java.util.UUID;

/** Un hechizo pendiente en la pila (el ultimo de la lista es el primero en resolverse). */
public class StackEntryDto {
    public UUID cardId;
    public String cardName;
    public UUID ownerId;
    public String spellSpeed;
    public List<UUID> targetIds;
}
