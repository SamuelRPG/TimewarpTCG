package com.saroga.timewarp.infrastructure.web.controller;

import com.saroga.timewarp.application.ports.in.PlayerInGameActions.MoveCard.MoveCardUseCase;
import com.saroga.timewarp.application.ports.in.PlayerInGameActions.PassPriority.PassPriorityUseCase;
import com.saroga.timewarp.application.ports.in.PlayerInGameActions.PlayCard.PlayCardUseCase;
import com.saroga.timewarp.application.ports.in.StartMatch.StartMatchUseCase;
import com.saroga.timewarp.application.ports.out.GameRepository.GameRepositoryPort;
import com.saroga.timewarp.application.service.BotBrain.BotBrainService;
import com.saroga.timewarp.domain.model.entity.card.Card;
import com.saroga.timewarp.domain.model.entity.gameState.GameState;
import com.saroga.timewarp.domain.model.entity.playerState.PlayerState;
import com.saroga.timewarp.domain.model.enums.CardLocation;
import com.saroga.timewarp.domain.model.enums.GameStatus;
import com.saroga.timewarp.infrastructure.web.dto.request.MoveCardRequestDto;
import com.saroga.timewarp.infrastructure.web.dto.request.PassPriorityRequestDto;
import com.saroga.timewarp.infrastructure.web.dto.request.PlayCardRequestDto;
import com.saroga.timewarp.infrastructure.web.dto.request.StartMatchRequestDto;
import com.saroga.timewarp.infrastructure.web.dto.response.ErrorResponseDto;
import com.saroga.timewarp.infrastructure.web.dto.response.GameStateDto;
import com.saroga.timewarp.infrastructure.web.mapper.GameStateMapper;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
@MessageMapping("/game")
public class GameController {

    private static final String PLAYER_TOPIC_TEMPLATE = "/topic/player/%s";
    private final MoveCardUseCase moveCardUseCase;
    private final PassPriorityUseCase passPriorityUseCase;
    private final PlayCardUseCase playCardUseCase;
    private final BotBrainService botBrainService;
    private final StartMatchUseCase startMatchUseCase;
    private final GameRepositoryPort gameRepository;
    private final SimpMessagingTemplate simpMessagingTemplate;

    public GameController ( MoveCardUseCase moveCardUseCase, PassPriorityUseCase passPriorityUseCase,
                            PlayCardUseCase playCardUseCase, BotBrainService botBrainService,
                            StartMatchUseCase startMatchUseCase, GameRepositoryPort gameRepository,
                            SimpMessagingTemplate simpMessagingTemplate) {
        this.moveCardUseCase = moveCardUseCase;
        this.passPriorityUseCase = passPriorityUseCase;
        this.playCardUseCase = playCardUseCase;
        this.botBrainService = botBrainService;
        this.startMatchUseCase = startMatchUseCase;
        this.gameRepository = gameRepository;
        this.simpMessagingTemplate = simpMessagingTemplate;
    }

    @MessageMapping("/playCard")
    public void playCard(@Payload PlayCardRequestDto request) {
        try {
            GameState game = gameRepository.findById(request.gameId);
            CardLocation destination = CardLocation.valueOf(request.destination);
            List<Card> targets = new ArrayList<>();
            if (request.targetIds != null && !request.targetIds.isEmpty()) {
                targets = request.targetIds.stream()
                        .map(c -> game.getCardById(c))
                        .collect(Collectors.toList());
            }
            playCardUseCase.execute(game, request.playerId, request.cardId, destination, targets);
            processGameUpdate(game);
        } catch (IllegalStateException | IllegalArgumentException e) {
            sendErrorToPlayer(request.playerId, e.getMessage());
        }
    }

    @MessageMapping("/moveCard")
    public void moveCard(@Payload MoveCardRequestDto request) {
        try {
            GameState game = gameRepository.findById(request.gameId);
            CardLocation destination = CardLocation.valueOf(request.destination);
            moveCardUseCase.execute(game, request.playerId, request.cardId, destination);
            processGameUpdate(game); // <-- USA EL HELPER
        } catch (IllegalStateException | IllegalArgumentException e) {
            sendErrorToPlayer(request.playerId, e.getMessage());
        }
    }

    @MessageMapping("/passPriority")
    public void passPriority(@Payload PassPriorityRequestDto request) {
        try {
            GameState game = gameRepository.findById(request.gameId);
            passPriorityUseCase.execute(game, request.playerId);
            processGameUpdate(game); // <-- USA EL HELPER
        } catch (IllegalStateException | IllegalArgumentException e) {
            sendErrorToPlayer(request.playerId, e.getMessage());
        }
    }

    @MessageMapping("/startLocalMatch")
    public void startMatch(@Payload StartMatchRequestDto request) {
        try {
            GameState game = startMatchUseCase.executeLocalMatch(request.playerId, request.deckName);
            processGameUpdate(game); // <-- USA EL HELPER
        } catch (Exception e) {
            // Si el mazo no existe, se lo notificamos al cliente para que no se quede colgado
            sendErrorToPlayer(request.playerId, "Error al iniciar partida: " + e.getMessage());
        }
    }

    // --- HELPER DEFLUJO Y LIMPIEZA DE RAM ---
    private void processGameUpdate(GameState game) {
        // 1. Si el humano ganó con su última acción
        if (game.getGameStatus() == GameStatus.FINISHED) {
            broadcastGame(game);
            gameRepository.delete(game.getGameId());
            return;
        }

        // 2. Si la partida sigue, guardamos y ejecutamos IA
        gameRepository.save(game);
        checkBotTurn(game);
        broadcastGame(game);

        // 3. Si el bot ganó en su turno, borramos también
        if (game.getGameStatus() == GameStatus.FINISHED) {
            gameRepository.delete(game.getGameId());
        }
    }

    private void checkBotTurn(GameState game) {
        PlayerState activePlayer = game.getPlayerById(game.getActivePlayerId());
        while (game.getPlayerById(game.getActivePlayerId()).isBot() && game.getGameStatus() != GameStatus.FINISHED) {
            botBrainService.takeTurn(game);
            gameRepository.save(game);
        }
    }

    private void broadcastGame(GameState game) {
        sendToPlayer(game, game.getPlayerA().getPlayerId());
        sendToPlayer(game, game.getPlayerB().getPlayerId());
    }

    private void sendToPlayer(GameState game, UUID playerId) {
        GameStateDto dto = GameStateMapper.toGameStateDto(game, playerId);
        // Ahora solo formateamos con el playerId
        String destination = String.format(PLAYER_TOPIC_TEMPLATE, playerId);
        simpMessagingTemplate.convertAndSend(destination, dto);
    }

    private void sendErrorToPlayer(UUID playerId, String errorMessage) {
        ErrorResponseDto errorDto = new ErrorResponseDto(errorMessage);
        String errorDestination = "/topic/player/" + playerId + "/errors";
        simpMessagingTemplate.convertAndSend(errorDestination, errorDto);
    }
}