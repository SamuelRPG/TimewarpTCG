package com.saroga.timewarp.infrastructure.web.controller;

import com.saroga.timewarp.application.ports.in.StartMatch.StartMatchUseCase;
import com.saroga.timewarp.application.ports.out.GameRepository.GameRepositoryPort;
import com.saroga.timewarp.application.service.Matchmaking.MatchmakingService;
import com.saroga.timewarp.application.service.Matchmaking.MatchmakingService.Pair;
import com.saroga.timewarp.application.service.Matchmaking.MatchmakingService.Waiting;
import com.saroga.timewarp.domain.model.entity.gameState.GameState;
import com.saroga.timewarp.domain.model.enums.GameStatus;
import com.saroga.timewarp.infrastructure.security.AuthPrincipal;
import com.saroga.timewarp.infrastructure.web.GameFlow;
import com.saroga.timewarp.infrastructure.web.dto.request.ConcedeRequestDto;
import com.saroga.timewarp.infrastructure.web.dto.request.JoinQueueRequestDto;
import com.saroga.timewarp.infrastructure.web.dto.response.MatchStatusDto;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.Optional;
import java.util.UUID;

/**
 * Partidas contra OTRA PERSONA ("partida rapida"): cola de emparejamiento, abandono y desconexion.
 * Una vez creada la partida, las acciones de juego son las mismas de siempre (/app/game/playCard, moveCard, passPriority):
 * GameController ya comprueba que el jugador pertenece a la partida y GameFlow envia a cada uno SU vista (sin ver la mano del rival).
 *
 * SEGURIDAD: igual que en GameController, el jugador sale SIEMPRE del token. Un jugador solo puede estar en una cola o
 * una partida a la vez, y el limite de mensajes por sesion (StompAuthInterceptor) tambien aplica aqui.
 */
@Controller
@MessageMapping("/game")
public class OnlineMatchController {

    private final MatchmakingService matchmaking;
    private final StartMatchUseCase startMatchUseCase;
    private final GameRepositoryPort gameRepository;
    private final GameFlow flow;

    public OnlineMatchController(MatchmakingService matchmaking, StartMatchUseCase startMatchUseCase,
                                 GameRepositoryPort gameRepository, GameFlow flow) {
        this.matchmaking = matchmaking;
        this.startMatchUseCase = startMatchUseCase;
        this.gameRepository = gameRepository;
        this.flow = flow;
    }

    @MessageMapping("/joinQueue")
    public void joinQueue(@Payload JoinQueueRequestDto request, Principal principal, @Header("simpSessionId") String sessionId) {
        UUID pid = GameFlow.playerOf(principal);
        if (pid == null) return;
        try {
            if (request != null && request.playerId != null && !request.playerId.equals(pid)) {
                throw new IllegalArgumentException("El jugador indicado en la petición no coincide con tu sesión.");
            }
            String deck = request == null || request.deckName == null ? "" : request.deckName.trim();
            boolean builtin = deck.equalsIgnoreCase("AGGRO") || deck.equalsIgnoreCase("CONTROL");
            AuthPrincipal who = principal instanceof AuthPrincipal ? (AuthPrincipal) principal : null;
            if (who != null && who.guest() && !builtin) {
                throw new IllegalArgumentException("Los invitados solo pueden jugar con AGGRO o CONTROL. Inicia sesión para usar tus mazos.");
            }
            startMatchUseCase.checkDeck(pid, deck);              // un mazo no valido se avisa YA, no al emparejar
            flow.leaveCurrentGame(pid);                          // una sola partida activa por jugador (si seguias en una, la pierdes)
            flow.bindSession(pid, sessionId);

            String name = who != null ? who.username() : "Jugador";
            Optional<Pair> pair = matchmaking.join(pid, name, deck);
            if (pair.isEmpty()) {
                flow.sendStatus(pid, new MatchStatusDto("QUEUED", null, "Buscando rival…"));
                return;
            }
            startVersus(pair.get());
        } catch (IllegalStateException | IllegalArgumentException e) {
            flow.sendError(pid, "No se pudo buscar partida: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("[ERROR] Fallo al buscar partida: " + e);
            e.printStackTrace();
            flow.sendError(pid, "Error interno del servidor.");
        }
    }

    private void startVersus(Pair pair) {
        Waiting a = pair.first(), b = pair.second();
        GameState game;
        try {
            game = startMatchUseCase.executeVersusMatch(a.playerId(), a.deckName(), b.playerId(), b.deckName());
        } catch (RuntimeException e) {
            // el mazo de alguno dejo de ser valido entre que entro en la cola y ahora: se avisa a los dos
            String msg = "No se pudo crear la partida: " + e.getMessage();
            flow.sendError(a.playerId(), msg);
            flow.sendError(b.playerId(), msg);
            return;
        }
        gameRepository.deleteByPlayerId(a.playerId());
        gameRepository.deleteByPlayerId(b.playerId());
        synchronized (game) {
            gameRepository.save(game);
            flow.sendStatus(a.playerId(), new MatchStatusDto("MATCHED", b.name(), "¡Rival encontrado!"));
            flow.sendStatus(b.playerId(), new MatchStatusDto("MATCHED", a.name(), "¡Rival encontrado!"));
            flow.afterAction(game);                              // estado inicial para los dos (cada uno ve el suyo)
        }
    }

    @MessageMapping("/leaveQueue")
    public void leaveQueue(Principal principal) {
        UUID pid = GameFlow.playerOf(principal);
        if (pid == null) return;
        matchmaking.leave(pid);
        flow.sendStatus(pid, new MatchStatusDto("LEFT", null, "Has salido de la cola."));
    }

    /** Abandonar la partida en curso (contra otra persona o contra el bot): el rival gana. */
    @MessageMapping("/concede")
    public void concede(@Payload ConcedeRequestDto request, Principal principal) {
        UUID pid = GameFlow.playerOf(principal);
        if (pid == null) return;
        try {
            if (request != null && request.playerId != null && !request.playerId.equals(pid)) {
                throw new IllegalArgumentException("El jugador indicado en la petición no coincide con tu sesión.");
            }
            Optional<GameState> found = gameRepository.findByPlayerId(pid);
            if (found.isEmpty()) return;                                         // ya no hay partida: nada que abandonar
            GameState game = found.get();
            if (request != null && request.gameId != null && !request.gameId.equals(game.getGameId())) return;   // era de otra partida
            synchronized (game) {
                game.concede(pid);
                flow.afterAction(game);
            }
        } catch (Exception e) {
            System.err.println("[ERROR] Fallo al abandonar una partida: " + e);
            flow.sendError(pid, "Error interno del servidor.");
        }
    }

    /**
     * Una sesion se cierra (cierra la ventana, se cae la red, vuelve al menu): sale de la cola y, si estaba en una partida
     * contra otra persona, la pierde. Solo cuenta si es la sesion con la que empezo: una sesion vieja que se cierra
     * tarde no debe tumbar la partida nueva.
     */
    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        UUID pid = GameFlow.playerOf(event.getUser());
        if (pid == null) return;
        String current = flow.sessionOf(pid);
        if (current == null || !current.equals(event.getSessionId())) return;
        flow.dropSession(pid);
        matchmaking.leave(pid);
        gameRepository.findByPlayerId(pid).ifPresent(game -> {
            synchronized (game) {
                if (game.getGameStatus() == GameStatus.FINISHED) return;
                if (GameFlow.isPvp(game)) {
                    game.concede(pid);
                    flow.afterAction(game);
                } else {
                    gameRepository.delete(game.getGameId());         // partida contra el bot sin nadie: se libera la memoria
                }
            }
        });
    }

    /** Quien lleva demasiado esperando en la cola sale con un aviso. */
    @Scheduled(fixedDelayString = "${timewarp.pvp.sweep-ms:5000}")
    public void expireQueue() {
        for (Waiting w : matchmaking.expire()) {
            flow.sendStatus(w.playerId(), new MatchStatusDto("TIMEOUT", null, "No se ha encontrado rival. Vuelve a intentarlo."));
        }
    }
}
