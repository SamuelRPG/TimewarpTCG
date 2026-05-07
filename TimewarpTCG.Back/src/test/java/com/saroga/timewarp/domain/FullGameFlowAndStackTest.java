package com.saroga.timewarp.domain;

import com.saroga.timewarp.domain.model.effect.effectInterface.Effect;
import com.saroga.timewarp.domain.model.effect.variable.allyBoost.AllyBoostEffect;
import com.saroga.timewarp.domain.model.effect.variable.enemyDamageDealt.EnemyDamageDealtEffect;
import com.saroga.timewarp.domain.model.entity.card.Card;
import com.saroga.timewarp.domain.model.entity.gameState.GameState;
import com.saroga.timewarp.domain.model.entity.playerState.PlayerState;
import com.saroga.timewarp.domain.model.enums.*;
import com.saroga.timewarp.domain.model.vo.card.Cost;
import com.saroga.timewarp.domain.model.vo.card.Drive;
import com.saroga.timewarp.domain.model.vo.card.TargetingRequirement;
import com.saroga.timewarp.domain.model.vo.playerState.Resource;
import com.saroga.timewarp.domain.service.CardPlayService;
import com.saroga.timewarp.domain.service.CombatService;
import com.saroga.timewarp.domain.service.GameFlowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class FullGameFlowAndStackTest {

    private UUID idA, idB;
    private GameState game;
    private CardPlayService cardPlayService;
    private GameFlowService gameFlowService;
    private CombatService combatService;

    @BeforeEach
    void setUp() {
        idA = UUID.randomUUID();
        idB = UUID.randomUUID();
        cardPlayService = new CardPlayService();
        gameFlowService = new GameFlowService();
        combatService = new CombatService();

        List<TargetingRequirement> targetEnemy = List.of(new TargetingRequirement(1, TargetGroup.ENEMY));
        List<TargetingRequirement> targetAlly = List.of(new TargetingRequirement(1, TargetGroup.ALLY));

        // JUGADOR A: Tanque de 5 de vida, Rayo Rápido de 2 daño, Escudo Instantáneo de +2 vida
        Card unitA = Card.createUnit(idA, "Tanque A", Drive.of(5), Cost.of(1), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        Card fastDamageA = Card.createSpell(idA, "Rayo A", Cost.of(1), List.of(new EnemyDamageDealtEffect(Drive.of(2))), targetEnemy, SpellSpeed.FAST);
        Card instantBuffA = Card.createSpell(idA, "Escudo A", Cost.of(1), List.of(new AllyBoostEffect(Drive.of(2))), targetAlly, SpellSpeed.INSTANT);

        // JUGADOR B: Guerrero de 4 de vida, Bola de Fuego Rápida de 3 daño
        Card unitB = Card.createUnit(idB, "Guerrero B", Drive.of(4), Cost.of(1), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        Card fastDamageB = Card.createSpell(idB, "Bola Fuego B", Cost.of(1), List.of(new EnemyDamageDealtEffect(Drive.of(3))), targetEnemy, SpellSpeed.FAST);

        PlayerState pA = new PlayerState(idA, new ArrayList<>(List.of(unitA, fastDamageA, instantBuffA)), true); // A empieza la ronda 1
        PlayerState pB = new PlayerState(idB, new ArrayList<>(List.of(unitB, fastDamageB)), false);

        pA.setResource(Resource.of(10));
        pB.setResource(Resource.of(10));

        game = new GameState(UUID.randomUUID(), pA, pB);

        // Robamos todas las cartas preparadas a la mano
        for (int i=0; i<3; i++) pA.drawCard();
        for (int i=0; i<2; i++) pB.drawCard();
    }

    @Test
    void testEpicBattleAndRoundAlternation() {
        // --- 1. DESPLIEGUE ---
        Card unitA = game.getPlayerA().getHand().stream().filter(Card::isUnit).findFirst().get();
        Card fastDamageA = game.getPlayerA().getHand().stream().filter(c -> c.getName().equals("Rayo A")).findFirst().get();
        Card instantBuffA = game.getPlayerA().getHand().stream().filter(c -> c.getName().equals("Escudo A")).findFirst().get();

        Card unitB = game.getPlayerB().getHand().stream().filter(Card::isUnit).findFirst().get();
        Card fastDamageB = game.getPlayerB().getHand().stream().filter(c -> c.getName().equals("Bola Fuego B")).findFirst().get();

        // A juega y mueve, B juega y mueve
        cardPlayService.executePlay(game, idA, unitA, CardLocation.MY_BASE, new ArrayList<>());
        unitA.ready();
        cardPlayService.executePlay(game, idB, unitB, CardLocation.MY_BASE, new ArrayList<>());
        unitB.ready();

        game.getPlayerA().moveCard(unitA, CardLocation.FIELD_LEFT);
        gameFlowService.confirmMoveAndPassPriority(game, idA, combatService);
        game.getPlayerB().moveCard(unitB, CardLocation.FIELD_LEFT);
        gameFlowService.confirmMoveAndPassPriority(game, idB, combatService);

        assertEquals(GamePhase.COMBAT_OPEN, game.getGamePhase());

        // --- 2. LA PILA MÁGICA ---
        // A pasa la prioridad inicial del combate
        gameFlowService.confirmMoveAndPassPriority(game, idA, combatService);

        // B lanza la Bola de Fuego a la unidad A (FAST)
        cardPlayService.executePlay(game, idB, fastDamageB, CardLocation.FIELD_LEFT, List.of(unitA));
        assertEquals(GamePhase.COMBAT_CLOSED, game.getGamePhase());
        assertEquals(1, game.getSpellStack().size()); // 1 hechizo en pila

        // A responde lanzando su Rayo a la unidad B (FAST)
        cardPlayService.executePlay(game, idA, fastDamageA, CardLocation.FIELD_LEFT, List.of(unitB));
        assertEquals(2, game.getSpellStack().size()); // 2 hechizos en pila

        // B decide no hacer nada más y pasa
        gameFlowService.confirmMoveAndPassPriority(game, idB, combatService);

        // A lanza el Escudo Instantáneo sobre su propia unidad
        cardPlayService.executePlay(game, idA, instantBuffA, CardLocation.FIELD_LEFT, List.of(unitA));
        assertEquals(2, game.getSpellStack().size(), "El INSTANT no debe ir a la pila");
        assertEquals(7, unitA.getDrive().value(), "El INSTANT debe haber sumado vida de inmediato (5+2=7)");

        // A ya no hace nada más y pasa. ¡Se resuelve la pila!
        gameFlowService.confirmMoveAndPassPriority(game, idA, combatService);

        // --- 3. COMPROBACIÓN DE RESOLUCIÓN LIFO ---
        assertTrue(game.isStackEmpty(), "La pila debería estar vacía tras resolverse");
        assertEquals(GamePhase.COMBAT_OPEN, game.getGamePhase(), "Deberíamos volver a COMBAT_OPEN");

        // El Rayo de A debió resolverse primero: Quitó 2 a la unidad B (4-2 = 2)
        assertEquals(2, unitB.getDrive().value(), "La unidad B debió recibir 2 de daño del rayo primero");

        // La Bola de Fuego de B se resolvió después: Quitó 3 a la unidad A (7-3 = 4)
        assertEquals(4, unitA.getDrive().value(), "La unidad A debió recibir 3 de daño de la bola de fuego (7-3=4)");

        // --- 4. RESOLUCIÓN FÍSICA ---
        // Ambos pasan en COMBAT_OPEN para que se peguen a puñetazos
        gameFlowService.confirmMoveAndPassPriority(game, game.getActivePlayerId(), combatService);
        gameFlowService.confirmMoveAndPassPriority(game, game.getActivePlayerId(), combatService);

        assertEquals(GamePhase.MAIN_OPEN, game.getGamePhase());

        // Unidad A (vida 4) pega 4 a Unidad B (vida 2). B muere.
        // Unidad B (vida 2) pega 2 a Unidad A (vida 4). A sobrevive con 2.
        assertTrue(unitB.isDestroyed(), "La unidad B debería haber muerto en combate físico");
        assertFalse(unitA.isDestroyed(), "La unidad A debería sobrevivir");
        assertEquals(2, unitA.getDrive().value(), "La unidad A debería haber quedado con 2 de vida (4-2=2)");

        // --- 5. CAMBIO DE RONDA ---
        // Pasan los dos para acabar la ronda
        gameFlowService.confirmMoveAndPassPriority(game, game.getActivePlayerId(), combatService);
        gameFlowService.confirmMoveAndPassPriority(game, game.getActivePlayerId(), combatService);

        // Comprobamos la maravilla: La nueva ronda empezó y la prioridad la tiene B
        assertEquals(1, game.getTurnNumber().value(), "Deberíamos estar en el turno 1");
        assertTrue(game.getPlayerB().hasPriority(), "El jugador B debería empezar con prioridad esta nueva ronda porque A empezó la anterior");
        assertFalse(game.getPlayerA().hasPriority());
    }
}