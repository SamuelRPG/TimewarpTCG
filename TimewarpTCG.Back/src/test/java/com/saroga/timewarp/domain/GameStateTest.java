package com.saroga.timewarp.domain;

import com.saroga.timewarp.domain.model.entity.card.Card;
import com.saroga.timewarp.domain.model.entity.gameState.GameState;
import com.saroga.timewarp.domain.model.entity.playerState.PlayerState;
import com.saroga.timewarp.domain.model.enums.CardType;
import com.saroga.timewarp.domain.model.vo.card.Cost;
import com.saroga.timewarp.domain.model.vo.card.Drive;
import com.saroga.timewarp.domain.model.vo.playerState.Resource;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class GameStateTest {

    @Test
    void should_ReduceDriveAndDestroyCard_When_DamageIsDealt() {
        // 1. ARRANGE (Preparación)
        UUID playerId = UUID.randomUUID();
        Drive initialDrive = Drive.of(5);
        Cost cost = Cost.of(2);

        // Creamos una carta de tipo UNIT
        Card warrior = new Card(UUID.randomUUID(), playerId, "Guerrero", initialDrive, cost, CardType.UNIT);

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
}
