package com.saroga.timewarp.application;

import com.saroga.timewarp.application.service.Matchmaking.MatchmakingService;
import com.saroga.timewarp.application.service.Matchmaking.MatchmakingService.Pair;
import com.saroga.timewarp.domain.model.entity.gameState.GameState;
import com.saroga.timewarp.domain.model.entity.playerState.PlayerState;
import com.saroga.timewarp.domain.model.enums.GameStatus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/** Cola de "partida rapida" y abandono de partida. */
public class MatchmakingServiceTest {

    private final AtomicLong now = new AtomicLong(1_000);
    private final MatchmakingService queue = new MatchmakingService(60_000, now::get);
    private final UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();

    @Test
    void firstPlayerWaits_secondOneIsPairedWithHim() {
        assertTrue(queue.join(a, "Ana", "AGGRO").isEmpty());
        assertTrue(queue.isWaiting(a));

        Optional<Pair> pair = queue.join(b, "Beto", "CONTROL");
        assertTrue(pair.isPresent());
        assertEquals(a, pair.get().first().playerId(), "el que esperaba va primero");
        assertEquals(b, pair.get().second().playerId());
        assertEquals("CONTROL", pair.get().second().deckName());
        assertEquals(0, queue.size(), "los dos salen de la cola");
    }

    @Test
    void youCannotBePairedWithYourself_andPressingAgainJustRefreshes() {
        assertTrue(queue.join(a, "Ana", "AGGRO").isEmpty());
        assertTrue(queue.join(a, "Ana", "CONTROL").isEmpty(), "volver a pulsar no te empareja contigo");
        assertEquals(1, queue.size());
    }

    @Test
    void thirdPlayerWaitsForTheNextOne() {
        queue.join(a, "Ana", "AGGRO");
        queue.join(b, "Beto", "AGGRO");
        assertTrue(queue.join(c, "Cris", "AGGRO").isEmpty(), "A y B ya estan jugando");
        assertTrue(queue.isWaiting(c));
    }

    @Test
    void leavingRemovesYouFromTheQueue() {
        queue.join(a, "Ana", "AGGRO");
        assertTrue(queue.leave(a));
        assertFalse(queue.leave(a), "salir dos veces no hace nada");
        assertTrue(queue.join(b, "Beto", "AGGRO").isEmpty(), "Ana ya no esta: Beto espera");
    }

    @Test
    void waitingTooLongExpires() {
        queue.join(a, "Ana", "AGGRO");
        now.addAndGet(30_000);
        queue.join(b, "Beto", "AGGRO");          // se empareja con Ana: nadie caduca
        assertTrue(queue.expire().isEmpty());

        queue.join(c, "Cris", "AGGRO");
        now.addAndGet(61_000);
        var gone = queue.expire();
        assertEquals(1, gone.size());
        assertEquals(c, gone.get(0).playerId());
        assertEquals(0, queue.size());
    }

    @Test
    void concedeEndsTheGameAndTheRivalWins_onlyOnce() {
        PlayerState pa = new PlayerState(a, new ArrayList<>(), true, false);
        PlayerState pb = new PlayerState(b, new ArrayList<>(), false, false);
        GameState game = new GameState(UUID.randomUUID(), pa, pb);
        assertEquals(GameStatus.ONGOING, game.getGameStatus());

        game.concede(a);
        assertEquals(GameStatus.FINISHED, game.getGameStatus());
        assertEquals(b, game.getWinnerId());

        game.concede(b);                          // ya habia acabado: el resultado no cambia
        assertEquals(b, game.getWinnerId());
    }
}
