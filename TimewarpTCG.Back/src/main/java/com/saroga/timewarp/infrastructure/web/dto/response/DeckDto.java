package com.saroga.timewarp.infrastructure.web.dto.response;

import java.util.List;

/** Un mazo guardado: nombre, total de cartas, facciones usadas y su contenido agrupado por carta. */
public class DeckDto {
    public String name;
    public int total;
    public List<String> factions;
    public List<DeckEntryDto> cards;
}
