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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class CombatAndMagicInteractionTest {

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

            // 1. REQUISITOS DE OBJETIVOS
            List<TargetingRequirement> fastSpellReqs = List.of(new TargetingRequirement(1, TargetGroup.ENEMY));
            List<TargetingRequirement> instantSpellReqs = List.of(new TargetingRequirement(1, TargetGroup.ALLY));

            // 2. EFECTOS REALES (Los que me has pasado)
            List<Effect> buffEffect = List.of(new AllyBoostEffect(Drive.of(2)));
            List<Effect> damageEffect = List.of(new EnemyDamageDealtEffect(Drive.of(2)));

            // 3. CREACIÓN DE CARTAS
            // Guerrero A: 3 de Drive
            Card unitA = Card.createUnit(uuidA, "Guerrero A", Drive.of(3), Cost.of(1),
                    new ArrayList<>(), new ArrayList<>(), new ArrayList<>());

            // Muro B: 4 de Drive
            Card unitB = Card.createUnit(uuidB, "Muro B", Drive.of(4), Cost.of(1),
                    new ArrayList<>(), new ArrayList<>(), new ArrayList<>());

            // Hechizos con sus efectos reales
            Card instantSpell = Card.createSpell(uuidA, "Fuerza Bruta", Cost.of(1),
                    buffEffect, instantSpellReqs, SpellSpeed.INSTANT);

            Card fastSpell = Card.createSpell(uuidB, "Rayo Debilitador", Cost.of(1),
                    damageEffect, fastSpellReqs, SpellSpeed.FAST);

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
        void testRealCombatWithSpellDamageAndBuffs() {
            // --- FASE 1: DESPLIEGUE EN BASE ---
            pA.drawCard();
            Card unitA = pA.getHand().getFirst();
            cardPlayService.executePlay(gameState, uuidA, unitA, CardLocation.MY_BASE, new ArrayList<>());
            pA.readyAllCards();

            pB.drawCard();
            Card unitB = pB.getHand().getFirst();
            cardPlayService.executePlay(gameState, uuidB, unitB, CardLocation.MY_BASE, new ArrayList<>());
            pB.readyAllCards();

            // --- FASE 2: MOVIMIENTO AL COMBATE ---
            pA.moveCard(unitA, CardLocation.FIELD_LEFT);
            pA.confirmMoveAndPassPriority(gameState, combatService);

            pB.moveCard(unitB, CardLocation.FIELD_LEFT);
            pB.confirmMoveAndPassPriority(gameState, combatService);

            assertEquals(GamePhase.COMBAT_OPEN, gameState.getGamePhase());

            // --- FASE 3: BATALLA MÁGICA ---
            // A no hace nada inicial y pasa prioridad a B
            pA.confirmMoveAndPassPriority(gameState, combatService);

            // Turno de B: Lanza Rayo (-2 Daño a unitA)
            pB.drawCard();
            Card fastSpell = pB.getHand().getFirst();
            cardPlayService.executePlay(gameState, uuidB, fastSpell, CardLocation.FIELD_LEFT, List.of(unitA));

            // El hechizo va a la pila. unitA todavía tiene 3 de Drive.
            assertEquals(3, unitA.getDrive().value());

            // Turno de A: Lanza Buff (+2 Drive a unitA)
            pA.drawCard();
            Card instantSpell = pA.getHand().getFirst();
            cardPlayService.executePlay(gameState, uuidA, instantSpell, CardLocation.FIELD_LEFT, List.of(unitA));

            // ASSERT MAGIA INSTANTÁNEA: Como es INSTANT, se aplica al momento. 3 + 2 = 5 Drive.
            assertEquals(5, unitA.getDrive().value());

            // --- FASE 4: RESOLUCIÓN DE LA PILA ---
            pA.confirmMoveAndPassPriority(gameState, combatService); // A pasa
            pB.confirmMoveAndPassPriority(gameState, combatService); // B pasa

            // ASSERT MAGIA RÁPIDA: La pila explota. El Rayo hace 2 de daño. 5 - 2 = 3 Drive.
            assertTrue(gameState.isStackEmpty());
            assertEquals(3, unitA.getDrive().value()); // Volvió a 3 gracias al daño de B

            // --- FASE 5: RESOLUCIÓN FÍSICA (COMBATE) ---
            pA.confirmMoveAndPassPriority(gameState, combatService); // A pasa
            pB.confirmMoveAndPassPriority(gameState, combatService); // B pasa

            // ASSERT COMBATE: Las tortas se han repartido. Fase vuelve a MAIN_OPEN.
            assertEquals(GamePhase.MAIN_OPEN, gameState.getGamePhase());

            // COMPROBACIONES FINALES DE VIDA Y MUERTE:

            // 1. unitA (3) pegó a unitB (4). unitB sobrevive con 1 de vida.
            assertTrue(pB.getFieldLeft().contains(unitB));
            assertFalse(unitB.isDestroyed());
            assertEquals(1, unitB.getDrive().value(), "El Muro B debería sobrevivir con 1 de Drive");

            // 2. unitB (4) pegó a unitA (3). unitA muere, se destruye y va al cementerio.
            assertFalse(pA.getFieldLeft().contains(unitA), "El Guerrero A debería haber desaparecido del campo");
            assertTrue(unitA.isDestroyed(), "La carta del Guerrero A debería estar marcada como destruida");
            assertTrue(pA.getGraveyard().contains(unitA), "El Guerrero A debería estar en el cementerio");
        }
}
