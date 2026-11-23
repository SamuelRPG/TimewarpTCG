package com.saroga.timewarp.infrastructure.web.dto.response;

public class DeckEntryDto {
    public String cardId;
    public int quantity;

    public DeckEntryDto() {}

    public DeckEntryDto(String cardId, int quantity) {
        this.cardId = cardId;
        this.quantity = quantity;
    }
}
