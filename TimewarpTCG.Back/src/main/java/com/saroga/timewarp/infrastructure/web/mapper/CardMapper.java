package com.saroga.timewarp.infrastructure.web.mapper;

import com.saroga.timewarp.domain.model.entity.card.Card;
import com.saroga.timewarp.infrastructure.web.dto.response.CardDto;

import java.util.List;
import java.util.stream.Collectors;

public class CardMapper {

    public static CardDto toCardDto(Card card) {
        CardDto dto = new CardDto();
        dto.id = card.getId();
        dto.name = card.getName();
        dto.currentCost = card.getCost().value();
        dto.currentDrive = card.getDrive().value();
        dto.isExhausted = card.isExhausted();
        return dto;
    }

    public static List<CardDto> toCardList (List<Card> cards) {
        return cards.stream()
                .map(card -> toCardDto(card))
                .collect(Collectors.toList());
    }
}
