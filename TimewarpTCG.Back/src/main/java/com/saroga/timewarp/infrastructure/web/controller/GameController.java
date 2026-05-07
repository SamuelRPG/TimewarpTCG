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
import com.saroga.timewarp.infrastructure.web.dto.request.MoveCardRequestDto;
import com.saroga.timewarp.infrastructure.web.dto.request.PassPriorityRequestDto;
import com.saroga.timewarp.infrastructure.web.dto.request.PlayCardRequestDto;
import com.saroga.timewarp.infrastructure.web.dto.request.StartMatchRequestDto;
import com.saroga.timewarp.infrastructure.web.dto.response.CardDto;
import com.saroga.timewarp.infrastructure.web.dto.response.GameStateDto;
import com.saroga.timewarp.infrastructure.web.mapper.CardMapper;
import com.saroga.timewarp.infrastructure.web.mapper.GameStateMapper;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
@MessageMapping("/game")
public class GameController {
    //ruta de envio de informacion saliente a Unity
    private static final String PLAYER_TOPIC_TEMPLATE = "/topic/game/%s/player/%s";

    private final MoveCardUseCase moveCardUseCase;
    private final PassPriorityUseCase passPriorityUseCase;
    private final PlayCardUseCase playCardUseCase;
    private final BotBrainService botBrainService;
    private final StartMatchUseCase startMatchUseCase;
    private final GameRepositoryPort gameRepository;
    private final SimpMessagingTemplate simpMessagingTemplate;

    public GameController ( MoveCardUseCase moveCardUseCase,
                            PassPriorityUseCase passPriorityUseCase,
                            PlayCardUseCase playCardUseCase,
                            BotBrainService botBrainService,
                            StartMatchUseCase startMatchUseCase,
                            GameRepositoryPort gameRepository,
                            SimpMessagingTemplate simpMessagingTemplate
                            ) {
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
        GameState game = gameRepository.findById(request.gameId);
        CardLocation destination = CardLocation.valueOf(request.destination);

        List<Card> targets = new ArrayList<>();

        if (!request.targetIds.isEmpty()) {
            targets = request.targetIds.stream()
                    .map(c -> game.getCardById(c))
                    .collect(Collectors.toList());
        }
        playCardUseCase.execute(game, request.playerId, request.cardId, destination, targets);
        gameRepository.save(game);
        checkBotTurn(game);
        broadcastGame(game);
    }

    @MessageMapping("/moveCard")
    public void moveCard(@Payload MoveCardRequestDto request) {
        GameState game = gameRepository.findById(request.gameId);
        CardLocation destination = CardLocation.valueOf(request.destination);

        moveCardUseCase.execute(game, request.playerId, request.cardId, destination);
        gameRepository.save(game);
        checkBotTurn(game);
        broadcastGame(game);
    }

    @MessageMapping("/passPriority")
    public void passPriority(@Payload PassPriorityRequestDto request) {
       GameState game = gameRepository.findById(request.gameId);
       passPriorityUseCase.execute(game, request.playerId);

       gameRepository.save(game);
       checkBotTurn(game);
       broadcastGame(game);
    }

    @MessageMapping("/startLocalMatch")
    public void startMatch(@Payload StartMatchRequestDto request) {
        GameState game = startMatchUseCase.executeLocalMatch(request.playerId, request.deckName);
        gameRepository.save(game);
        checkBotTurn(game);
        broadcastGame(game);
    }

    private void checkBotTurn(GameState game) {
        PlayerState activePlayer = game.getPlayerById(game.getActivePlayerId());

        while (game.getPlayerById(game.getActivePlayerId()).isBot()) {
            botBrainService.takeTurn(game);
            gameRepository.save(game);
        }
    }

    private void broadcastGame(GameState game) {
        sendToPlayer(game, game.getPlayerA().getPlayerId());
        sendToPlayer(game, game.getPlayerB().getPlayerId());


    //el dia que sea multijugador, aqui tambien habra que añadir la ruta del jugador B. En este caso el jugador A siempre es el humano
        simpMessagingTemplate.convertAndSend("/topic/player/" + game.getPlayerA().getPlayerId(),
                GameStateMapper.toGameStateDto(game, game.getPlayerA().getPlayerId()));
    }
    private void sendToPlayer(GameState game, UUID playerId) {
        GameStateDto dto = GameStateMapper.toGameStateDto(game, playerId);
        String destination = String.format(PLAYER_TOPIC_TEMPLATE, game.getGameId(), playerId);
        simpMessagingTemplate.convertAndSend(destination, dto);
    }
}
