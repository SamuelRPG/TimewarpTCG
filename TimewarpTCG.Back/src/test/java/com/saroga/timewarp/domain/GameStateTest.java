package com.saroga.timewarp.domain;

import com.saroga.timewarp.domain.model.effect.charge.ChargeEffect;
import com.saroga.timewarp.domain.model.effect.effectInterface.Effect;
import com.saroga.timewarp.domain.model.effect.glory.GloryEffect;
import com.saroga.timewarp.domain.model.entity.card.Card;
import com.saroga.timewarp.domain.model.entity.gameState.GameState;
import com.saroga.timewarp.domain.model.entity.playerState.PlayerState;
import com.saroga.timewarp.domain.model.enums.CardLocation;
import com.saroga.timewarp.domain.model.enums.CardType;
import com.saroga.timewarp.domain.model.vo.card.Cost;
import com.saroga.timewarp.domain.model.vo.card.Drive;
import com.saroga.timewarp.domain.model.vo.playerState.Resource;
import com.saroga.timewarp.domain.service.CombatService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class GameStateTest {

    @Test
    void should_ReduceDriveAndDestroyCard_When_DamageIsDealt() {
        // 1. ARRANGE (Preparación)
        UUID playerId = UUID.randomUUID();
        Drive initialDrive = Drive.of(5);
        Cost cost = Cost.of(2);

        // Creamos una carta de tipo UNIT
        Card warrior = new Card(UUID.randomUUID(), playerId, "Guerrero", initialDrive, initialDrive, cost, CardType.UNIT);

        // 2. ACT (Acción)
        warrior.takeDamage(Drive.of(3)); // Recibe 3 de daño

        // 3. ASSERT (Verificación)
        assertEquals(2, warrior.getDrive().value(), "El drive debería ser 2");
        assertTrue(warrior.isAlive(), "La carta debería seguir viva");

        // Acción final: daño mortal
        warrior.takeDamage(Drive.of(2));
        assertFalse(warrior.isAlive(), "La carta debería estar muerta");
    }

    @Test
    void should_SpendResources_When_PlayerPlaysCard() {
        // Preparación
        List<Card> deck = new ArrayList<>();
        PlayerState player = new PlayerState(UUID.randomUUID(), deck, true);
        Cost cardCost = Cost.of(3);

        // Simulamos que el jugador tiene 5 de energía (Resource)
        player.setResource(Resource.of(5));

        // Acción: El jugador gasta energía para jugar algo de coste 3
        player.setResource(player.getResource().spend(cardCost));

        // Verificación
        assertEquals(2, player.getResource().resource(), "Deberían quedar 2 de energía");
    }

    @Test
    void should_SwitchPriority_When_TurnEnds() {
        // 1. ARRANGE (Preparamos los jugadores)
        PlayerState pA = new PlayerState(UUID.randomUUID(), new ArrayList<>(), true); // Empieza con prioridad
        PlayerState pB = new PlayerState(UUID.randomUUID(), new ArrayList<>(), false);
        GameState game = new GameState(UUID.randomUUID(), pA, pB);

        // 2. ACT (Cambiamos la prioridad)
        game.switchPriority();

        // 3. ASSERT (Verificamos que pA ya no tiene prioridad y pB sí)
        assertThat(pA.hasPriority()).isFalse();
        assertThat(pB.hasPriority()).isTrue();
        assertThat(game.getActivePlayerId()).isEqualTo(pB.getPlayerId());
    }

    @Test
    void should_ThrowException_When_PlayerHasNotEnoughResource() {
        // ARRANGE
        PlayerState player = new PlayerState(UUID.randomUUID(), new ArrayList<>(), true);
        player.setResource(Resource.of(2)); // Solo tiene 2 de energía
        Cost expensiveCardCost = Cost.of(5); // La carta cuesta 5

        // ACT & ASSERT
        // Verificamos que al intentar gastar, salta la excepción que pusimos en el Record
        assertThrows(IllegalStateException.class, () -> {
            player.setResource(player.getResource().spend(expensiveCardCost));
        }, "Debería haber lanzado una excepción por falta de recursos");
    }

    @Test
    void card_should_be_ready_after_new_turn() {
        // 1. ARRANGE
        Card unit = new Card(UUID.randomUUID(), UUID.randomUUID(), "Soldier", Drive.of(2), Drive.of(2), Cost.of(1), CardType.UNIT);
        unit.exhaust(); // La marcamos como cansada

        PlayerState pA = new PlayerState(UUID.randomUUID(), new ArrayList<>(), true);
        pA.getFieldLeft().add(unit);
        GameState game = new GameState(UUID.randomUUID(), pA, new PlayerState(UUID.randomUUID(), new ArrayList<>(), false));

        // 2. ACT
        game.newTurn();

        // 3. ASSERT
        assertThat(unit.isExhausted()).isFalse();
    }

    @Test
    void should_UpdateGameCorrectly_When_NewTurnStarts() {
        // 1. ARRANGE
        List<Card> deckA = new ArrayList<>();
        deckA.add(new Card(UUID.randomUUID(), UUID.randomUUID(), "Test Card",Drive.of(1), Drive.of(1), Cost.of(1), CardType.UNIT));

        PlayerState pA = new PlayerState(UUID.randomUUID(), deckA, true);
        PlayerState pB = new PlayerState(UUID.randomUUID(), new ArrayList<>(), false);
        GameState game = new GameState(UUID.randomUUID(), pA, pB);

        // Simulamos que pA ya tiene una unidad en el campo izquierdo
        Card unit = new Card(UUID.randomUUID(), pA.getPlayerId(), "Soldier", Drive.of(2), Drive.of(2), Cost.of(1), CardType.UNIT);
        pA.getFieldLeft().add(unit);

        // 2. ACT
        game.newTurn();

        // 3. ASSERT
        // Comprobamos que el turno es 1 (empezó en 0)
        assertThat(game.getTurnNumber().value()).isEqualTo(1);

        // Comprobamos que pA ha ganado 1 punto por tener unidad en FieldLeft
        assertThat(pA.getPoints().value()).isEqualTo(1);

        // Comprobamos que ha robado la carta del deck
        assertThat(pA.getHand()).hasSize(1);
        assertThat(pA.getDeck()).isEmpty();

        // Comprobamos que la energía se ha actualizado al turno 1
        assertThat(pA.getResource().resource()).isEqualTo(1);
    }

    @Test
    void should_ExecuteFullRoundFlowCorrectly() {
        // 1. ARRANGE
        Card unit = new Card(UUID.randomUUID(), UUID.randomUUID(), "Soldier", Drive.of(5), Drive.of(5), Cost.of(1), CardType.UNIT);
        // Asumimos que al crearla o entrar en mano/base nace con exhausted = true
        unit.exhaust();

        List<Card> deckA = new ArrayList<>(List.of(unit));
        PlayerState pA = new PlayerState(UUID.randomUUID(), deckA, true);
        PlayerState pB = new PlayerState(UUID.randomUUID(), new ArrayList<>(), false);
        GameState game = new GameState(UUID.randomUUID(), pA, pB);

        // 2. ACT: Comienza el turno (Esto debería hacer 'ready()' a las cartas si ya estuvieran, pero aquí roba)
        game.newTurn();

        // 3. Jugar carta a la Base (Nace agotada según tu regla)
        pA.getHand().remove(unit);
        pA.getBase().add(unit);
        unit.setCardLocation(CardLocation.MY_BASE);
        unit.exhaust(); // Nos aseguramos de que está agotada al entrar

        // 4. VERIFICACIÓN: Intentar moverla de Base a Field inmediatamente debería FALLAR
        assertThrows(IllegalStateException.class, () -> {
            pA.moveCard(unit, CardLocation.FIELD_LEFT);
        }, "No debería poder moverse el mismo turno que entra si nace agotada");

        // 5. PASO DE TURNO: Para que se recupere
        game.newTurn();
        assertThat(unit.isExhausted()).isFalse(); // El newTurn la ha despertado

        // 6. MOVIMIENTO: Ahora sí, de Base a Field Left
        pA.moveCard(unit, CardLocation.FIELD_LEFT);

        // 7. ASSERT: Verificar estado tras movimiento
        assertThat(pA.getBase()).isEmpty();
        assertThat(pA.getFieldLeft()).contains(unit);
        assertThat(unit.isExhausted()).isTrue(); // Se agota de nuevo por moverse

        // 8. FINAL: Comprobamos puntos en el siguiente ciclo
        game.newTurn();
        assertThat(pA.getPoints().value()).isEqualTo(1);
    }

        @Test
        void testCombatWithPermanentChargeAndGlory() {
            // 1. CONFIGURACIÓN
            UUID idA = UUID.randomUUID();
            UUID idB = UUID.randomUUID();
            CombatService combatService = new CombatService();

            // Creamos los efectos reales
            Effect chargePermanente = new ChargeEffect(); // Implementa beforeCombat { owner.buffDrive(1) }
            Effect glory = new GloryEffect();             // Implementa onCombatWin { owner.ready() }

            // Jugador A: Caballero con 5 de Drive base
            Card caballeroA = new Card(UUID.randomUUID(), idA, "Caballero",
                    Drive.of(5), Drive.of(5), Cost.of(3),
                    CardType.UNIT, new ArrayList<>(), List.of(chargePermanente, glory));
            caballeroA.exhaust(); // Empieza agotado

            // Jugador B: Orco con 3 de Drive base
            Card orcoB = new Card(UUID.randomUUID(), idB, "Orco",
                    Drive.of(3), Drive.of(3), Cost.of(2),
                    CardType.UNIT, new ArrayList<>(), new ArrayList<>());

            // Inicializamos estados (usando tu constructor: UUID, deck, hasPriority)
            PlayerState pA = new PlayerState(idA, new ArrayList<>(), true);
            PlayerState pB = new PlayerState(idB, new ArrayList<>(), false);
            GameState gameState = new GameState(UUID.randomUUID(), pA, pB);

            // Posicionamos en el campo
            pA.addCardToLocation(caballeroA, CardLocation.FIELD_LEFT);
            pB.addCardToLocation(orcoB, CardLocation.FIELD_LEFT);

            // 2. EJECUCIÓN DEL COMBATE
            // Paso interno 1: beforeCombat -> Caballero sube de 5 a 6 permanentemente.
            // Paso interno 2: Cálculo -> 6 vs 3.
            // Paso interno 3: Daño -> Caballero recibe 3 de daño.
            combatService.resolveLaneCombat(gameState, CardLocation.FIELD_LEFT);

            // 3. VERIFICACIONES

            // El Orco recibe 6 de daño (muere)
            assertTrue(orcoB.isDestroyed(), "El Orco debería haber muerto");

            // El Caballero:
            // Su Drive era 5 -> Charge lo subió a 6 -> Recibió 3 de daño -> Queda en 3.
            assertFalse(caballeroA.isDestroyed(), "El Caballero debería sobrevivir");
            assertEquals(3, caballeroA.getDrive().value(), "El Caballero debería tener 3 de vida (6 iniciales tras Charge - 3 de daño)");

            // GLORY: Al ganar y sobrevivir, se activa y se pone Ready
            assertFalse(caballeroA.isExhausted(), "El Caballero debería estar READY por el efecto GLORY");
        }
}
