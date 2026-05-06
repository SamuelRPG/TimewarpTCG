package com.saroga.timewarp.application;

import com.saroga.timewarp.application.factory.DeckFactory;
import com.saroga.timewarp.application.service.StartMatch.StartMatchService;
import com.saroga.timewarp.domain.model.entity.card.Card;
import com.saroga.timewarp.domain.model.entity.gameState.GameState;
import com.saroga.timewarp.domain.model.enums.*;
import com.saroga.timewarp.domain.model.vo.card.Cost;
import com.saroga.timewarp.domain.service.CardPlayService;
import com.saroga.timewarp.domain.service.CombatService;
import com.saroga.timewarp.domain.service.GameFlowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class RandomStartAndFlowTest {

    private StartMatchService startMatchService;
    private CardPlayService cardPlayService;
    private GameFlowService gameFlowService;
    private CombatService combatService;

    @BeforeEach
    void setUp() {
        // Inicializamos los servicios puros (sin Spring Boot, súper rápido)
        DeckFactory deckFactory = new DeckFactory();
        startMatchService = new StartMatchService(deckFactory);
        cardPlayService = new CardPlayService();
        combatService = new CombatService();
        gameFlowService = new GameFlowService();
    }

    @Test
    void testRandomPriorityAndSpellFlow() {
        int aStarts = 0;
        int bStarts = 0;
        GameState gameWhereBotStarts = null;

        // --- 1. PROBAR LA ALEATORIEDAD ---
        // Generamos 50 partidas para comprobar que la moneda funciona
        for (int i = 0; i < 50; i++) {
            GameState game = startMatchService.executeLocalMatch(UUID.randomUUID(), "CONTROL");
            if (game.getPlayerA().hasPriority()) {
                aStarts++;
            } else {
                bStarts++;
                if (gameWhereBotStarts == null) {
                    gameWhereBotStarts = game; // Nos guardamos esta partida para la parte 2
                }
            }
        }

        System.out.println("En 50 sorteos: Humano empezó " + aStarts + " veces, Bot " + bStarts + " veces.");
        assertTrue(aStarts > 0, "El Humano debería empezar en algún momento");
        assertTrue(bStarts > 0, "El Bot debería empezar en algún momento");
        assertNotNull(gameWhereBotStarts, "Deberíamos haber obtenido al menos una partida donde empiece el Bot");


        // --- 2. PROBAR EL FLUJO CON EL BOT EMPEZANDO ---
        GameState game = gameWhereBotStarts;
        UUID humanId = game.getPlayerA().getPlayerId();
        UUID botId = game.getPlayerB().getPlayerId();

        // Comprobamos que el sistema sabe que es el turno de B
        assertTrue(game.getPlayerB().hasPriority());
        assertEquals(botId, game.getActivePlayerId());

        // TURNO DE B: Juega una unidad
        Card botUnit = game.getPlayerB().getHand().stream().filter(Card::isUnit).findFirst().get();
        cardPlayService.executePlay(game, botId, botUnit, CardLocation.MY_BASE, new ArrayList<>());
        botUnit.ready();

        // La prioridad debe haber saltado a A
        assertTrue(game.getPlayerA().hasPriority());
        assertEquals(humanId, game.getActivePlayerId());

        // TURNO DE A: Juega una unidad
        Card humanUnit = game.getPlayerA().getHand().stream().filter(Card::isUnit).findFirst().get();
        cardPlayService.executePlay(game, humanId, humanUnit, CardLocation.MY_BASE, new ArrayList<>());
        humanUnit.ready();

        // Prioridad vuelve a B. B la mueve al campo izquierdo
        assertTrue(game.getPlayerB().hasPriority());
        game.getPlayerB().moveCard(botUnit, CardLocation.FIELD_LEFT);
        gameFlowService.confirmMoveAndPassPriority(game, botId, combatService);

        // Prioridad pasa a A. A la mueve al campo izquierdo para defender
        assertTrue(game.getPlayerA().hasPriority());
        game.getPlayerA().moveCard(humanUnit, CardLocation.FIELD_LEFT);
        gameFlowService.confirmMoveAndPassPriority(game, humanId, combatService);

        // ¡SE ABRE EL COMBATE! (Porque hay cartas de ambos en FIELD_LEFT)
        assertEquals(GamePhase.COMBAT_OPEN, game.getGamePhase(), "El juego debe detectar el combate");

        // B decide no hacer nada y pasa prioridad
        gameFlowService.confirmMoveAndPassPriority(game, botId, combatService);
        assertTrue(game.getPlayerA().hasPriority());

        // A decide usar un hechizo RÁPIDO (Añadimos un hechizo falso a su mano para el test)
        // A decide usar un hechizo RÁPIDO (Añadimos un hechizo falso a su mano para el test)
        Card fastSpell = Card.createSpell(humanId, "Rayo Test", Cost.of(1), new ArrayList<>(), new ArrayList<>(), SpellSpeed.FAST);

        // Lo añadimos a la lista de la mano
        game.getPlayerA().getHand().add(fastSpell);

        // ¡LA CLAVE! Le decimos a la carta internamente que ya está en la mano
        fastSpell.setCardLocation(CardLocation.HAND);

        cardPlayService.executePlay(game, humanId, fastSpell, CardLocation.FIELD_LEFT, new ArrayList<>());

        // Verificamos que la fase se bloquea porque hay algo en la Pila Mágica
        assertEquals(GamePhase.COMBAT_CLOSED, game.getGamePhase(), "El hechizo FAST bloquea la fase (COMBAT_CLOSED)");
        assertFalse(game.isStackEmpty(), "Debería haber un hechizo en la pila");

        // B no tiene respuestas y pasa. A tampoco tiene más respuestas y pasa.
        gameFlowService.confirmMoveAndPassPriority(game, botId, combatService);
        gameFlowService.confirmMoveAndPassPriority(game, humanId, combatService);

        // Verificamos que la Pila se resuelve
        assertTrue(game.isStackEmpty(), "La pila debería resolverse y vaciarse");
        assertEquals(GamePhase.COMBAT_OPEN, game.getGamePhase(), "Volvemos a COMBAT_OPEN tras resolver la magia");

        // Ambos pasan prioridad de nuevo para que los muñecos se peguen
        gameFlowService.confirmMoveAndPassPriority(game, game.getActivePlayerId(), combatService);
        gameFlowService.confirmMoveAndPassPriority(game, game.getActivePlayerId(), combatService);

        // ¡COMBATE TERMINADO!
        assertEquals(GamePhase.MAIN_OPEN, game.getGamePhase(), "Volvemos a MAIN_OPEN tras los puñetazos");
    }
}
