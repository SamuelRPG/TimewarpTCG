package com.saroga.timewarp.infrastructure.web.mapper;

import com.saroga.timewarp.domain.model.entity.playerState.PlayerState;
import com.saroga.timewarp.infrastructure.web.dto.response.PlayerDto;

public class PlayerMapper {

    public static PlayerDto toPlayerDto(PlayerState player, boolean isOwner) {
        PlayerDto dto = new PlayerDto();
        dto.id = player.getPlayerId();
        dto.score = player.getScore().value();
        dto.resource = player.getResource().value();
        dto.hasPriority = player.hasPriority();

        dto.deckSize = player.getDeck().size();
        dto.graveyardSize = player.getGraveyard().size();
        dto.handSize = player.getHand().size();

        dto.baseZone = CardMapper.toCardList(player.getBase());
        dto.fieldLeft = CardMapper.toCardList(player.getFieldLeft());
        dto.fieldRight = CardMapper.toCardList(player.getFieldRight());

        if (isOwner) {
            dto.hand = CardMapper.toCardList(player.getHand());
        } else dto.hand = null;

        return dto;
    }
}
