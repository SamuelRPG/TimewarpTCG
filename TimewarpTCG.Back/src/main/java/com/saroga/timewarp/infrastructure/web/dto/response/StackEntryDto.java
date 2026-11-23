package com.saroga.timewarp.infrastructure.web.dto.response;

import java.util.List;
import java.util.UUID;

/**
 * Una entrada de la pila (el ULTIMO de la lista es el primero en resolverse).
 * kind: SPELL = un hechizo lanzado; TRIGGER = el efecto "al entrar en juego" de una unidad que ya esta en el tablero.
 * Todo lo que lleva (texto, coste, objetivos...) es informacion publica desde que la carta se lanza: sirve para que los DOS jugadores
 * vean que va a pasar, tambien con los hechizos del rival, que no estan en tu mano.
 */
public class StackEntryDto {
    public UUID cardId;
    public String cardName;
    public UUID ownerId;
    public String spellSpeed;
    public List<UUID> targetIds;

    public String kind;                 // SPELL | TRIGGER
    public String description;          // lo que hace la carta (el texto de la carta)
    public int currentCost;
    public String faction;
    public String cardType;             // SPELL | UNIT
    public List<String> keywords;
    public List<String> targetNames;    // nombres de las unidades elegidas como objetivo
}
