package com.saroga.timewarp.application.service.PlayerInGameActions.PlayCard;

import com.saroga.timewarp.application.ports.in.PlayerInGameActions.PlayCard.PlayCardUseCase;
import com.saroga.timewarp.domain.model.entity.card.Card;
import com.saroga.timewarp.domain.model.entity.gameState.GameState;
import com.saroga.timewarp.domain.model.entity.playerState.PlayerState;
import com.saroga.timewarp.domain.model.enums.CardLocation;
import com.saroga.timewarp.domain.service.CardPlayService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

public class PlayCardService implements PlayCardUseCase {
    private final CardPlayService cardPlayService; // El servicio de tu Dominio

    public PlayCardService(CardPlayService cardPlayService) {
        this.cardPlayService = cardPlayService;
    }

    @Override
    public void execute(GameState game, UUID playerId, UUID cardId, CardLocation destination, List<Card> targets) {
        PlayerState actor = game.getPlayerById(playerId);

        Card cardToPlay = actor.getHand().stream()
                .filter(c -> c.getId().equals(cardId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("La carta no está en la mano"));

        cardPlayService.executePlay(game, playerId, cardToPlay, destination, targets);
    }
}
