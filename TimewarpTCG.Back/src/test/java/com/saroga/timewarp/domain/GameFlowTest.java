package com.saroga.timewarp.domain;

import com.saroga.timewarp.domain.model.effect.keyword.charge.ChargeEffect;
import com.saroga.timewarp.domain.model.effect.effectInterface.Effect;
import com.saroga.timewarp.domain.model.effect.keyword.glory.GloryEffect;
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
    private GameState gameState;
    private PlayerState pA;
    private PlayerState pB;

    @BeforeEach
    void setUp() {
        uuidA = UUID.randomUUID();
        uuidB = UUID.randomUUID();
        combatService = new CombatService();
        cardPlayService = new CardPlayService();

        // 1. REQUISITOS DE OBJETIVOS (Targeting Requirements)
        // Asumiendo que tu constructor/record de TargetingRequirement recibe (TargetGroup, cantidad)
        List<TargetingRequirement> fastSpellReqs = List.of(new TargetingRequirement( 1, TargetGroup.ENEMY));
        List<TargetingRequirement> instantSpellReqs = List.of(new TargetingRequirement( 1, TargetGroup.ALLY));

        // 2. EFECTOS DUMMY (Para que la lista de efectos no esté vacía y tenga lógica si se llama a onPlaced)
        List<Effect> dummyDamageEffect = List.of(new Effect() {
            @Override
            public void onPlaced(Card card, PlayerState owner, PlayerState opponent, CardLocation lane, List<Card> targets) {
                System.out.println("Efecto ejecutado sobre: " + targets.getFirst().getName());
            }
        });

        // 3. CREACIÓN DE CARTAS
        Card unitA = Card.createUnit(uuidA, "Guerrero Temporal", Drive.of(3), Cost.of(1),
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>()); // Unidades sin targets en este test

        Card unitB = Card.createUnit(uuidB, "Muro Defensivo", Drive.of(5), Cost.of(1),
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>());

        Card fastSpell = Card.createSpell(uuidB, "Rayo Desintegrador", Cost.of(1),
                dummyDamageEffect, fastSpellReqs, SpellSpeed.FAST); // Requiere 1 Enemigo

        Card instantSpell = Card.createSpell(uuidA, "Impulso Táctico", Cost.of(1),
                dummyDamageEffect, instantSpellReqs, SpellSpeed.INSTANT); // Requiere 1 Aliado

        // 4. SETUP DE MAZOS Y ESTADO
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
        Card unitA = pA.getHand().getFirst(); // Declaramos unitA por primera vez aquí
        cardPlayService.executePlay(gameState, uuidA, unitA, CardLocation.MY_BASE, new ArrayList<>());
        pA.readyAllCards();

        // ASSERT: El servicio pasó la prioridad a B
        assertTrue(pB.hasPriority());

        pB.drawCard();
        Card unitB = pB.getHand().getFirst(); // Declaramos unitB por primera vez aquí
        cardPlayService.executePlay(gameState, uuidB, unitB, CardLocation.MY_BASE, new ArrayList<>());
        pB.readyAllCards();

        // ASSERT: El servicio devolvió la prioridad a A
        assertTrue(pA.hasPriority());

        // --- FASE 2: MOVIMIENTO Y DECLARACIÓN DE COMBATE ---

        // 1. TURNO DE A (Tiene la prioridad)
        assertTrue(pA.hasPriority());
        pA.moveCard(unitA, CardLocation.FIELD_LEFT);
        pA.confirmMoveAndPassPriority(gameState, combatService);

        // ASSERT: Como B aún no ha subido al frente, no hay combate todavía.
        assertEquals(GamePhase.MAIN_OPEN, gameState.getGamePhase());
        assertTrue(pB.hasPriority()); // La bola pasa a B

        // 2. TURNO DE B
        pB.moveCard(unitB, CardLocation.FIELD_LEFT); // B sube a defender
        pB.confirmMoveAndPassPriority(gameState, combatService);

        // ASSERT: ¡BUM! Al confirmar B, el motor detecta que hay unidades de ambos.
        assertEquals(GamePhase.COMBAT_OPEN, gameState.getGamePhase());
        assertTrue(pA.hasPriority()); // La bola vuelve a A
        assertEquals(ConsecutivePasses.ZERO, gameState.getConsecutivePasses());

        // --- FASE 3: LA RESPUESTA MÁGICA (FAST) ---
        // A tiene la prioridad, pero decide no jugar nada y le pasa la bola a B
        pA.confirmMoveAndPassPriority(gameState, combatService);
        assertTrue(pB.hasPriority()); // Ahora sí, es el turno de B

        // Turno de B: roba y lanza su hechizo
        pB.drawCard();
        Card fastSpell = pB.getHand().getFirst();

        // JUGAMOS HECHIZO: Destino = FIELD_LEFT (simbólico), Target = unitA (Enemigo de B)
        cardPlayService.executePlay(gameState, uuidB, fastSpell, CardLocation.FIELD_LEFT, List.of(unitA));

        // ASSERT: Bloqueo de fase y cambio de prioridad
        assertEquals(GamePhase.COMBAT_CLOSED, gameState.getGamePhase());
        assertFalse(gameState.isStackEmpty());
        assertTrue(pA.hasPriority()); // Al jugar un Fast/Slow, la prioridad vuelve a A

        // --- FASE 4: EL CONTRAATAQUE (INSTANT) ---
        pA.drawCard();
        Card instantSpell = pA.getHand().getFirst();

        // JUGAMOS HECHIZO: Destino = FIELD_LEFT, Target = unitA (Aliado de A)
        cardPlayService.executePlay(gameState, uuidA, instantSpell, CardLocation.FIELD_LEFT, List.of(unitA));

        // ASSERT: El INSTANT se ejecuta, NO va a la pila, MANTIENE prioridad y fase
        assertEquals(GamePhase.COMBAT_CLOSED, gameState.getGamePhase());
        assertTrue(pA.hasPriority());

        // --- FASE 5: RESOLUCIÓN DE LA PILA ---
        pA.confirmMoveAndPassPriority(gameState, combatService); // A pasa
        pB.confirmMoveAndPassPriority(gameState, combatService); // B pasa

        // ASSERT: resolveQueue() explota la pila. Volvemos a OPEN.
        assertTrue(gameState.isStackEmpty());
        assertEquals(GamePhase.COMBAT_OPEN, gameState.getGamePhase());
        assertEquals(ConsecutivePasses.ZERO, gameState.getConsecutivePasses());
        assertTrue(pA.hasPriority());

        // --- FASE 6: RESOLUCIÓN FÍSICA (COMBATE) ---
        pA.confirmMoveAndPassPriority(gameState, combatService); // A pasa
        pB.confirmMoveAndPassPriority(gameState, combatService); // B pasa

        // ASSERT: handlePhaseEnd dispara el combate físico. Vuelta a MAIN_OPEN.
        assertEquals(GamePhase.MAIN_OPEN, gameState.getGamePhase());
        assertEquals(ConsecutivePasses.ZERO, gameState.getConsecutivePasses());
    }
}
