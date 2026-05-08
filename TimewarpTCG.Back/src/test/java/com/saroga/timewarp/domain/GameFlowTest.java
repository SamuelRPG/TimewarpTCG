package com.saroga.timewarp.domain;

import com.saroga.timewarp.domain.model.effect.effectInterface.Effect;
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

public class GameFlowTest {
    private UUID uuidA;
    private UUID uuidB;
    private CombatService combatService;
    private CardPlayService cardPlayService;
    private GameFlowService gameFlowService; // <-- NUEVO SERVICIO
    private GameState gameState;
    private PlayerState pA;
    private PlayerState pB;

    @BeforeEach
    void setUp() {
        uuidA = UUID.randomUUID();
        uuidB = UUID.randomUUID();
        combatService = new CombatService();
        cardPlayService = new CardPlayService();
        gameFlowService = new GameFlowService(); // <-- INICIALIZADO

        List<TargetingRequirement> fastSpellReqs = List.of(new TargetingRequirement( 1, TargetGroup.ENEMY));
        List<TargetingRequirement> instantSpellReqs = List.of(new TargetingRequirement( 1, TargetGroup.ALLY));

        List<Effect> dummyDamageEffect = List.of(new Effect() {
            @Override
            public void onPlaced(GameState game,Card card, PlayerState owner, PlayerState opponent, CardLocation lane, List<Card> targets) {
                System.out.println("Efecto ejecutado sobre: " + targets.getFirst().getName());
            }
        });

        Card unitA = Card.createUnit(uuidA, "Guerrero Temporal", Drive.of(3), Cost.of(1),
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), Faction.ANCIENT_EGYPT);

        Card unitB = Card.createUnit(uuidB, "Muro Defensivo", Drive.of(5), Cost.of(1),
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), Faction.ANCIENT_EGYPT);

        Card fastSpell = Card.createSpell(uuidB, "Rayo Desintegrador", Cost.of(1),
                dummyDamageEffect, fastSpellReqs, SpellSpeed.FAST, Faction.ANCIENT_EGYPT);

        Card instantSpell = Card.createSpell(uuidA, "Impulso Táctico", Cost.of(1),
                dummyDamageEffect, instantSpellReqs, SpellSpeed.INSTANT, Faction.ANCIENT_EGYPT);

        List<Card> deckA = new ArrayList<>(List.of(unitA, instantSpell));
        List<Card> deckB = new ArrayList<>(List.of(unitB, fastSpell));

        pA = new PlayerState(uuidA, deckA, true);
        pB = new PlayerState(uuidB, deckB, false);

        pA.setResource(Resource.of(10));
        pB.setResource(Resource.of(10));

        gameState = new GameState(UUID.randomUUID(), pA, pB);
    }

    @Test
    void testFullCombatAndMagicCycle() {
        // --- FASE 1: DESPLIEGUE EN BASE ---
        pA.drawCard();
        Card unitA = pA.getHand().getFirst();
        cardPlayService.executePlay(gameState, uuidA, unitA, CardLocation.MY_BASE, new ArrayList<>());
        pA.readyAllCards();

        assertTrue(pB.hasPriority());

        pB.drawCard();
        Card unitB = pB.getHand().getFirst();
        cardPlayService.executePlay(gameState, uuidB, unitB, CardLocation.MY_BASE, new ArrayList<>());
        pB.readyAllCards();

        assertTrue(pA.hasPriority());

        // --- FASE 2: MOVIMIENTO Y DECLARACIÓN DE COMBATE ---
        assertTrue(pA.hasPriority());
        pA.moveCard(unitA, CardLocation.FIELD_LEFT);
        gameFlowService.confirmMoveAndPassPriority(gameState, uuidA, combatService); // <-- CAMBIO AQUÍ

        assertEquals(GamePhase.MAIN_OPEN, gameState.getGamePhase());
        assertTrue(pB.hasPriority());

        pB.moveCard(unitB, CardLocation.FIELD_LEFT);
        gameFlowService.confirmMoveAndPassPriority(gameState, uuidB, combatService); // <-- CAMBIO AQUÍ

        assertEquals(GamePhase.COMBAT_OPEN, gameState.getGamePhase());
        assertTrue(pA.hasPriority());
        assertEquals(ConsecutivePasses.ZERO, gameState.getConsecutivePasses());

        // --- FASE 3: LA RESPUESTA MÁGICA (FAST) ---
        gameFlowService.confirmMoveAndPassPriority(gameState, uuidA, combatService); // <-- CAMBIO AQUÍ
        assertTrue(pB.hasPriority());

        pB.drawCard();
        Card fastSpell = pB.getHand().getFirst();

        cardPlayService.executePlay(gameState, uuidB, fastSpell, CardLocation.FIELD_LEFT, List.of(unitA));

        assertEquals(GamePhase.COMBAT_CLOSED, gameState.getGamePhase());
        assertFalse(gameState.isStackEmpty());
        assertTrue(pA.hasPriority());

        // --- FASE 4: EL CONTRAATAQUE (INSTANT) ---
        pA.drawCard();
        Card instantSpell = pA.getHand().getFirst();

        cardPlayService.executePlay(gameState, uuidA, instantSpell, CardLocation.FIELD_LEFT, List.of(unitA));

        assertEquals(GamePhase.COMBAT_CLOSED, gameState.getGamePhase());
        assertTrue(pA.hasPriority());

        // --- FASE 5: RESOLUCIÓN DE LA PILA ---
        gameFlowService.confirmMoveAndPassPriority(gameState, uuidA, combatService); // <-- CAMBIO AQUÍ
        gameFlowService.confirmMoveAndPassPriority(gameState, uuidB, combatService); // <-- CAMBIO AQUÍ

        assertTrue(gameState.isStackEmpty());
        assertEquals(GamePhase.COMBAT_OPEN, gameState.getGamePhase());
        assertEquals(ConsecutivePasses.ZERO, gameState.getConsecutivePasses());
        assertTrue(pA.hasPriority());

        // --- FASE 6: RESOLUCIÓN FÍSICA (COMBATE) ---
        gameFlowService.confirmMoveAndPassPriority(gameState, uuidA, combatService); // <-- CAMBIO AQUÍ
        gameFlowService.confirmMoveAndPassPriority(gameState, uuidB, combatService); // <-- CAMBIO AQUÍ

        assertEquals(GamePhase.MAIN_OPEN, gameState.getGamePhase());
        assertEquals(ConsecutivePasses.ZERO, gameState.getConsecutivePasses());
    }
}