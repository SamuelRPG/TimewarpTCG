package com.saroga.timewarp.infrastructure.web;

import com.saroga.timewarp.application.ports.out.GameRepository.GameRepositoryPort;
import com.saroga.timewarp.application.service.BotBrain.BotBrainService;
import com.saroga.timewarp.domain.model.entity.gameState.GameState;
import com.saroga.timewarp.domain.model.enums.GameStatus;
import com.saroga.timewarp.infrastructure.web.dto.response.ErrorResponseDto;
import com.saroga.timewarp.infrastructure.web.dto.response.GameStateDto;
import com.saroga.timewarp.infrastructure.web.dto.response.MatchStatusDto;
import com.saroga.timewarp.infrastructure.web.mapper.GameStateMapper;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Lo que pasa DESPUES de cada accion en una partida (contra el bot o contra otra persona): guardar, avisar a los dos
 * jugadores, dejar que el bot juegue, limpiar al terminar. Lo comparten GameController y OnlineMatchController.
 *
 * RITMO DEL BOT: antes el servidor ejecutaba toda la secuencia del bot dentro de la accion del jugador y mandaba UN solo
 * estado al final: el jugador no veia que habia hecho el bot ni cuando cambiaba la prioridad. Ahora el bot juega UNA accion cada
 * timewarp.bot.delay-ms (con algo de variacion) y tras cada una se emite el estado. Con 0 se juega de golpe, como antes (pruebas).
 */
@Component
public class GameFlow {

    private static final String PLAYER_TOPIC_TEMPLATE = "/topic/player/%s";
    private static final int MAX_BOT_STEPS = 500;       // tope de seguridad: el bot nunca puede dejar un bucle infinito

    private final GameRepositoryPort gameRepository;
    private final BotBrainService botBrainService;
    private final SimpMessagingTemplate messaging;
    private final long botDelayMs;
    private final long pvpTurnMillis;

    private final ScheduledExecutorService botExecutor = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "timewarp-bot");
        t.setDaemon(true);
        return t;
    });
    /** partidas entre personas en curso -> momento de la ultima accion (para expulsar al que se queda sin responder). */
    private final Map<UUID, Long> pvpLastActivity = new ConcurrentHashMap<>();
    /** jugador -> sesion STOMP con la que empezo su partida o su busqueda (una desconexion antigua no debe tocar la nueva). */
    private final Map<UUID, String> sessions = new ConcurrentHashMap<>();

    public GameFlow(GameRepositoryPort gameRepository, BotBrainService botBrainService, SimpMessagingTemplate messaging,
                    @Value("${timewarp.bot.delay-ms:700}") long botDelayMs,
                    @Value("${timewarp.pvp.turn-seconds:180}") long pvpTurnSeconds) {
        this.gameRepository = gameRepository;
        this.botBrainService = botBrainService;
        this.messaging = messaging;
        this.botDelayMs = botDelayMs;
        this.pvpTurnMillis = pvpTurnSeconds * 1000L;
    }

    @PreDestroy
    void shutdown() { botExecutor.shutdownNow(); }

    // ------------------------------------------------------------------ sesiones
    public static UUID playerOf(Principal principal) {
        if (principal == null) return null;
        try { return UUID.fromString(principal.getName()); } catch (IllegalArgumentException e) { return null; }
    }

    public void bindSession(UUID playerId, String sessionId) { if (playerId != null && sessionId != null) sessions.put(playerId, sessionId); }
    public String sessionOf(UUID playerId) { return sessions.get(playerId); }
    public void dropSession(UUID playerId) { sessions.remove(playerId); }

    /**
     * Antes de empezar OTRA partida o busqueda: si el jugador seguia en una partida contra una persona, cuenta como abandono
     * (el rival gana y se entera); si era contra el bot, simplemente se descarta.
     */
    public void leaveCurrentGame(UUID playerId) {
        gameRepository.findByPlayerId(playerId).ifPresent(game -> {
            synchronized (game) {
                if (game.getGameStatus() != GameStatus.FINISHED && isPvp(game)) {
                    game.concede(playerId);
                    afterAction(game);
                } else {
                    gameRepository.delete(game.getGameId());
                }
            }
        });
        gameRepository.deleteByPlayerId(playerId);           // por si quedase alguna mas (no deberia)
    }

    // ------------------------------------------------------------------ flujo de una partida
    public static boolean isPvp(GameState game) {
        return !game.getPlayerA().isBot() && !game.getPlayerB().isBot();
    }

    /** Llamar con la partida bloqueada (synchronized) tras cualquier accion. */
    public void afterAction(GameState game) {
        boolean pvp = isPvp(game);
        if (game.getGameStatus() == GameStatus.FINISHED) {
            broadcast(game);
            finish(game);
            return;
        }
        gameRepository.save(game);
        if (pvp) pvpLastActivity.put(game.getGameId(), System.currentTimeMillis());

        if (botDelayMs <= 0) {                       // modo inmediato (pruebas): todo el turno del bot de golpe
            int guard = 0;
            while (botHasTurn(game) && guard++ < MAX_BOT_STEPS) {
                botBrainService.takeTurn(game);
                gameRepository.save(game);
            }
            broadcast(game);
            if (game.getGameStatus() == GameStatus.FINISHED) finish(game);
            return;
        }
        broadcast(game);                             // el jugador ve ya el resultado de su accion...
        scheduleBotStep(game, 0);                    // ...y el bot juega paso a paso
    }

    private void finish(GameState game) {
        pvpLastActivity.remove(game.getGameId());
        gameRepository.delete(game.getGameId());
    }

    private static boolean botHasTurn(GameState game) {
        return game.getGameStatus() != GameStatus.FINISHED && game.getPlayerById(game.getActivePlayerId()).isBot();
    }

    private void scheduleBotStep(GameState game, int steps) {
        if (steps >= MAX_BOT_STEPS || !botHasTurn(game)) return;
        long delay = botDelayMs + ThreadLocalRandom.current().nextLong(botDelayMs / 3 + 1);
        botExecutor.schedule(() -> {
            boolean again;
            try {
                synchronized (game) {
                    if (!botHasTurn(game) || !stored(game)) return;       // acabo la partida o el jugador se fue
                    botBrainService.takeTurn(game);
                    gameRepository.save(game);
                    broadcast(game);
                    if (game.getGameStatus() == GameStatus.FINISHED) { finish(game); return; }
                    again = botHasTurn(game);
                }
            } catch (Exception e) {
                System.err.println("[ERROR] Fallo en un paso del bot: " + e);
                return;
            }
            if (again) scheduleBotStep(game, steps + 1);
        }, delay, TimeUnit.MILLISECONDS);
    }

    private boolean stored(GameState game) {
        try { gameRepository.findById(game.getGameId()); return true; }
        catch (IllegalStateException e) { return false; }
    }

    // ------------------------------------------------------------------ tiempo de respuesta en partidas entre personas
    /** Si quien tiene que actuar lleva demasiado sin hacerlo, pierde por incomparecencia. */
    @Scheduled(fixedDelayString = "${timewarp.pvp.sweep-ms:5000}")
    public void sweepIdlePvp() {
        long limit = System.currentTimeMillis() - pvpTurnMillis;
        for (Map.Entry<UUID, Long> e : pvpLastActivity.entrySet()) {
            if (e.getValue() >= limit) continue;
            GameState game;
            try { game = gameRepository.findById(e.getKey()); }
            catch (IllegalStateException gone) { pvpLastActivity.remove(e.getKey()); continue; }
            synchronized (game) {
                if (game.getGameStatus() == GameStatus.FINISHED) { pvpLastActivity.remove(e.getKey()); continue; }
                game.concede(game.getActivePlayerId());      // pierde quien tenia que actuar
                afterAction(game);
            }
        }
    }

    // ------------------------------------------------------------------ mensajes a los jugadores
    public void broadcast(GameState game) {
        sendState(game, game.getPlayerA().getPlayerId());
        sendState(game, game.getPlayerB().getPlayerId());
    }

    private void sendState(GameState game, UUID playerId) {
        GameStateDto dto = GameStateMapper.toGameStateDto(game, playerId);
        messaging.convertAndSend(String.format(PLAYER_TOPIC_TEMPLATE, playerId), dto);
    }

    public void sendError(UUID playerId, String message) {
        messaging.convertAndSend("/topic/player/" + playerId + "/errors", new ErrorResponseDto(message));
    }

    public void sendStatus(UUID playerId, MatchStatusDto status) {
        messaging.convertAndSend("/topic/player/" + playerId + "/status", status);
    }
}
