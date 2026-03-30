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
        assertEquals(2, player.getResource().value(), "Deberían quedar 2 de energía");
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
        assertThat(pA.getResource().value()).isEqualTo(1);
    }

    @Test
    void should_ExecuteFullRoundFlowCorrectly() {
        // 1. ARRANGE
        Card unit = new Card(UUID.randomUUID(), UUID.randomUUID(), "Soldier",
                Drive.of(5), Drive.of(5), Cost.of(1), CardType.UNIT);

        PlayerState pA = new PlayerState(UUID.randomUUID(), new ArrayList<>(List.of(unit)), true);
        PlayerState pB = new PlayerState(UUID.randomUUID(), new ArrayList<>(), false);
        GameState game = new GameState(UUID.randomUUID(), pA, pB);

        // Empieza el juego: Las cartas en mano están en DECK/HAND
        game.newTurn(); // Roba la carta y setea initialLocation = HAND

        // 2. Simulamos jugar la carta a la Base
        pA.getHand().remove(unit);
        pA.getBase().add(unit);
        unit.setCardLocation(CardLocation.MY_BASE);
        unit.exhaust(); // Nace agotada al entrar en juego

        // 3. Pasamos turno para que se recupere (Ready)
        game.newTurn();
        assertFalse(unit.isExhausted(), "Tras un nuevo turno, la unidad debe estar Ready");
        // En este punto, cardLocationAtBeginningOfRound = MY_BASE

        // 4. MOVIMIENTO: De Base a Field Left
        pA.moveCard(unit, CardLocation.FIELD_LEFT);

        // VERIFICACIÓN INTERMEDIA: ¡No debería estar agotada todavía!
        // El jugador aún puede arrepentirse en Unity.
        assertFalse(unit.isExhausted(), "No debe agotarse hasta que se confirme");

        // 5. ACT: Confirmamos el movimiento y pasamos prioridad
        pA.confirmMoveAndPassPriority(game);

        // 6. ASSERT: Ahora sí debe estar agotada y en su sitio
        assertTrue(unit.isExhausted(), "Tras confirmar, la unidad debe estar agotada");
        assertThat(pA.getBase()).isEmpty();
        assertThat(pA.getFieldLeft()).contains(unit);

        // 7. FINAL: Comprobamos puntos al final del ciclo
        game.newTurn();
        assertEquals(1, pA.getScore().value(), "Debería tener 1 punto por controlar el Field");
    }

    @Test
    void testCombatWithPermanentChargeAndGlory() {
        // 1. CONFIGURACIÓN
        UUID idA = UUID.randomUUID();
        UUID idB = UUID.randomUUID();
        CombatService combatService = new CombatService();

        // Efectos: Charge da +1 permanente al iniciar combate, Glory endereza al ganar
        Effect chargePermanente = new ChargeEffect();
        Effect glory = new GloryEffect();

        Card caballeroA = new Card(UUID.randomUUID(), idA, "Caballero",
                Drive.of(5), Drive.of(5), Cost.of(3),
                CardType.UNIT, new ArrayList<>(), List.of(chargePermanente, glory));

        Card orcoB = new Card(UUID.randomUUID(), idB, "Orco",
                Drive.of(3), Drive.of(3), Cost.of(2),
                CardType.UNIT, new ArrayList<>(), new ArrayList<>());

        PlayerState pA = new PlayerState(idA, new ArrayList<>(), true);
        PlayerState pB = new PlayerState(idB, new ArrayList<>(), false);
        GameState gameState = new GameState(UUID.randomUUID(), pA, pB);

        // Posicionamos manualmente (sin pasar por moveCard para este test)
        pA.addCardToLocation(caballeroA, CardLocation.FIELD_LEFT);
        caballeroA.setCardLocation(CardLocation.FIELD_LEFT);
        caballeroA.exhaust(); // Empieza cansado para probar el Glory

        pB.addCardToLocation(orcoB, CardLocation.FIELD_LEFT);
        orcoB.setCardLocation(CardLocation.FIELD_LEFT);

        // 2. EJECUCIÓN
        // IMPORTANTE: Disparamos el "Antes de combatir" para que el Charge sume +1
        caballeroA.onMovementConfirmed(pA, pB, caballeroA.getCardLocation());

        // Ahora el combate: 6 vs 3.
        // Caballero recibe 3 de daño ($6 - 3 = 3$). Orco recibe 6 ($3 - 6 = -3 \rightarrow$ Muere)
        combatService.resolveLaneCombat(gameState, CardLocation.FIELD_LEFT);

        // 3. VERIFICACIONES
        assertTrue(orcoB.isDestroyed(), "El Orco debería haber muerto");
        assertFalse(caballeroA.isDestroyed(), "El Caballero debería sobrevivir");

        // Verificamos vida: 5 (base) + 1 (charge) - 3 (daño orco) = 3
        assertEquals(3, caballeroA.getDrive().value(), "Vida: 5+1-3=3");

        // GLORY: Al ganar, el efecto Glory debió hacer caballero.ready()
        assertFalse(caballeroA.isExhausted(), "El Caballero debería estar READY por el Glory");
    }
}
