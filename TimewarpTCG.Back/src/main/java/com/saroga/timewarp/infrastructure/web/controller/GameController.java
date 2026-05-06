package com.saroga.timewarp.infrastructure.web.controller;

import com.saroga.timewarp.application.ports.in.PlayerInGameActions.MoveCard.MoveCardUseCase;
import com.saroga.timewarp.application.ports.in.PlayerInGameActions.PassPriority.PassPriorityUseCase;
import com.saroga.timewarp.application.ports.in.PlayerInGameActions.PlayCard.PlayCardUseCase;
import com.saroga.timewarp.application.ports.in.StartMatch.StartMatchUseCase;
import com.saroga.timewarp.application.ports.out.GameRepository.GameRepositoryPort;
import com.saroga.timewarp.application.service.BotBrain.BotBrainService;
import com.saroga.timewarp.infrastructure.web.dto.request.PlayCardRequestDto;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
@MessageMapping("/game")
public class GameController {

    private final MoveCardUseCase moveCardUseCase;
    private final PassPriorityUseCase passPriorityUseCase;
    private final PlayCardUseCase playCardUseCase;
    private final BotBrainService botBrainService;
    private final StartMatchUseCase startMatchUseCase;
    private final GameRepositoryPort gameRepositoryPort;
    private final SimpMessagingTemplate simpMessagingTemplate;

    public GameController ( MoveCardUseCase moveCardUseCase,
                            PassPriorityUseCase passPriorityUseCase,
                            PlayCardUseCase playCardUseCase,
                            BotBrainService botBrainService,
                            StartMatchUseCase startMatchUseCase,
                            GameRepositoryPort gameRepositoryPort,
                            SimpMessagingTemplate simpMessagingTemplate
                            ) {
        this.moveCardUseCase = moveCardUseCase;
        this.passPriorityUseCase = passPriorityUseCase;
        this.playCardUseCase = playCardUseCase;
        this.botBrainService = botBrainService;
        this.startMatchUseCase = startMatchUseCase;
        this.gameRepositoryPort = gameRepositoryPort;
        this.simpMessagingTemplate = simpMessagingTemplate;
    }


    @MessageMapping("/playCard")
    public void playCard(@Payload PlayCardRequestDto dto) {

    }
}
