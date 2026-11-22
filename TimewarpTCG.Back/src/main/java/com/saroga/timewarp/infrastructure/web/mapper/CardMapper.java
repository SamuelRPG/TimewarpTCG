package com.saroga.timewarp.infrastructure.web.mapper;

import com.saroga.timewarp.domain.model.entity.card.Card;
import com.saroga.timewarp.infrastructure.web.dto.response.CardDto;
import com.saroga.timewarp.infrastructure.web.dto.response.TargetReqDto;

import java.util.List;
import java.util.stream.Collectors;

public class CardMapper {

    public static CardDto toCardDto(Card card) {
        CardDto dto = new CardDto();
        dto.id = card.getId();
        dto.name = card.getName();
        dto.description = card.getDescription();
        dto.cardType = card.getCardType().name();
        dto.currentCost = card.getCost().value();
        dto.currentDrive = card.getDrive().value();
        dto.isExhausted = card.isExhausted();
        dto.faction = card.getFaction().toString();
        dto.targetingRequirements = card.getTargetingRequirements().stream()
                .map(req -> req.toString()) // o req.toString(), dependiendo de cómo sea tu enum en Java
                .toList();
        dto.keywords = card.getKeywords().stream().map(Enum::name).toList();
        dto.spellSpeed = card.getSpellSpeed() != null ? card.getSpellSpeed().name() : "NOT_A_SPELL";
        dto.requirements = card.getTargetingRequirements().stream()
                .map(req -> new TargetReqDto(req.requiredCount(), req.targetGroup().name()))
                .toList();
        return dto;
    }

    public static List<CardDto> toCardList (List<Card> cards) {
        return cards.stream()
                .map(card -> toCardDto(card))
                .collect(Collectors.toList());
    }
}
