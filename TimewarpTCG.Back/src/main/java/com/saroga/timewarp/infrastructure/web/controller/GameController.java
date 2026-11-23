package com.saroga.timewarp.infrastructure.web.controller;

import com.saroga.timewarp.application.ports.in.PlayerInGameActions.MoveCard.MoveCardUseCase;
import com.saroga.timewarp.application.ports.in.PlayerInGameActions.PassPriority.PassPriorityUseCase;
import com.saroga.timewarp.application.ports.in.PlayerInGameActions.PlayCard.PlayCardUseCase;
import com.saroga.timewarp.application.ports.in.StartMatch.StartMatchUseCase;
import com.saroga.timewarp.application.ports.out.GameRepository.GameRepositoryPort;
import com.saroga.timewarp.domain.model.entity.card.Card;
import com.saroga.timewarp.domain.model.entity.gameState.GameState;
import com.saroga.timewarp.domain.model.enums.CardLocation;
import com.saroga.timewarp.domain.model.enums.GameStatus;
import com.saroga.timewarp.infrastructure.security.AuthPrincipal;
import com.saroga.timewarp.infrastructure.web.dto.request.MoveCardRequestDto;
import com.saroga.timewarp.infrastructure.web.dto.request.PassPriorityRequestDto;
import com.saroga.timewarp.infrastructure.web.dto.request.PlayCardRequestDto;
import com.saroga.timewarp.infrastructure.web.dto.request.StartMatchRequestDto;
import com.saroga.timewarp.infrastructure.web.GameFlow;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Acciones de partida por WebSocket. SEGURIDAD: el jugador es SIEMPRE el del token (Principal de la sesion STOMP);
 * el "playerId" que venga en el mensaje solo se admite si coincide, y la partida debe pertenecer a ese jugador.
 */
@Controller
@MessageMapping("/game")
public class GameController {

    private final MoveCardUseCase moveCardUseCase;
    private final PassPriorityUseCase passPriorityUseCase;
    private final PlayCardUseCase playCardUseCase;
    private final StartMatchUseCase startMatchUseCase;
    private final GameRepositoryPort gameRepository;
    private final GameFlow flow;

    public GameController(MoveCardUseCase moveCardUseCase, PassPriorityUseCase passPriorityUseCase,
                          PlayCardUseCase playCardUseCase, StartMatchUseCase startMatchUseCase,
                          GameRepositoryPort gameRepository, GameFlow flow) {
        this.moveCardUseCase = moveCardUseCase;
        this.passPriorityUseCase = passPriorityUseCase;
        this.playCardUseCase = playCardUseCase;
        this.startMatchUseCase = startMatchUseCase;
        this.gameRepository = gameRepository;
        this.flow = flow;
    }

    @MessageMapping("/playCard")
    public void playCard(@Payload PlayCardRequestDto request, Principal principal) {
        UUID pid = authenticated(principal);
        if (pid == null) return;
        try {
            checkSender(request == null ? null : request.playerId, pid);
            GameState game = ownGame(request.gameId, pid);
            synchronized (game) {
                CardLocation destination = CardLocation.valueOf(request.destination);
                List<Card> targets = new ArrayList<>();
                if (request.targetIds != null && !request.targetIds.isEmpty()) {
                    targets = request.targetIds.stream()
                            .map(c -> game.getCardById(c))
                            .collect(Collectors.toList());
                }
                playCardUseCase.execute(game, pid, request.cardId, destination, targets);
                processGameUpdate(game);
            }
        } catch (Exception e) {
            handle(pid, e);
        }
    }

    @MessageMapping("/moveCard")
    public void moveCard(@Payload MoveCardRequestDto request, Principal principal) {
        UUID pid = authenticated(principal);
        if (pid == null) return;
        try {
            checkSender(request == null ? null : request.playerId, pid);
            GameState game = ownGame(request.gameId, pid);
            synchronized (game) {
                CardLocation destination = CardLocation.valueOf(request.destination);
                moveCardUseCase.execute(game, pid, request.cardId, destination);
                processGameUpdate(game);
            }
        } catch (Exception e) {
            handle(pid, e);
        }
    }

    @MessageMapping("/passPriority")
    public void passPriority(@Payload PassPriorityRequestDto request, Principal principal) {
        UUID pid = authenticated(principal);
        if (pid == null) return;
        try {
            checkSender(request == null ? null : request.playerId, pid);
            GameState game = ownGame(request.gameId, pid);
            synchronized (game) {
                passPriorityUseCase.execute(game, pid);
                processGameUpdate(game);
            }
        } catch (Exception e) {
            handle(pid, e);
        }
    }

    @MessageMapping("/startLocalMatch")
    public void startMatch(@Payload StartMatchRequestDto request, Principal principal, @Header("simpSessionId") String sessionId) {
        UUID pid = authenticated(principal);
        if (pid == null) return;
        try {
            checkSender(request == null ? null : request.playerId, pid);
            String deck = request.deckName == null ? "" : request.deckName.trim();
            boolean builtin = deck.equalsIgnoreCase("AGGRO") || deck.equalsIgnoreCase("CONTROL");
            if (principal instanceof AuthPrincipal && ((AuthPrincipal) principal).guest() && !builtin) {
                throw new IllegalArgumentException("Los invitados solo pueden jugar con AGGRO o CONTROL. Inicia sesión para usar tus mazos.");
            }
            flow.leaveCurrentGame(pid);                    // solo una partida activa por jugador (si seguias en una online, la pierdes)
            flow.bindSession(pid, sessionId);
            GameState game = startMatchUseCase.executeLocalMatch(pid, deck, request.botDeck);
            synchronized (game) {
                processGameUpdate(game);
            }
        } catch (Exception e) {
            // Si el mazo no existe, se lo notificamos al cliente para que no se quede colgado
            if (e instanceof IllegalStateException || e instanceof IllegalArgumentException) {
                sendErrorToPlayer(pid, "Error al iniciar partida: " + e.getMessage());
            } else {
                handle(pid, e);
            }
        }
    }

    // ------------------------------------------------------------------ seguridad
    private static UUID authenticated(Principal principal) {
        if (principal == null) return null;     // el interceptor STOMP ya lo impide; defensa extra
        try { return UUID.fromString(principal.getName()); } catch (IllegalArgumentException e) { return null; }
    }

    private static void checkSender(UUID claimed, UUID actual) {
        if (claimed != null && !claimed.equals(actual)) {
            throw new IllegalArgumentException("El jugador indicado en la petición no coincide con tu sesión.");
        }
    }

    private GameState ownGame(UUID gameId, UUID playerId) {
        if (gameId == null) throw new IllegalArgumentException("Falta el identificador de la partida.");
        GameState game = gameRepository.findById(gameId);
        boolean participant = game.getPlayerA().getPlayerId().equals(playerId) || game.getPlayerB().getPlayerId().equals(playerId);
        if (!participant) {
            // mismo mensaje que si no existiera: no se revela que la partida existe
            throw new IllegalStateException("No se encuentra el id de este game state");
        }
        return game;
    }

    /** Errores de reglas -> texto para el jugador. Cualquier otro error: mensaje generico (el detalle va al log del servidor). */
    private void handle(UUID pid, Exception e) {
        if (e instanceof IllegalStateException || e instanceof IllegalArgumentException) {
            sendErrorToPlayer(pid, e.getMessage());
        } else {
            System.err.println("[ERROR] Excepcion no controlada en una accion de partida: " + e);
            e.printStackTrace();
            sendErrorToPlayer(pid, "Error interno del servidor.");
        }
    }

    // --- el flujo comun (guardar, avisar a los jugadores, pasos del bot, limpieza) vive en GameFlow ---
    private void processGameUpdate(GameState game) { flow.afterAction(game); }

    private void sendErrorToPlayer(UUID playerId, String errorMessage) { flow.sendError(playerId, errorMessage); }
}
