package com.saroga.timewarp.domain;

import com.saroga.timewarp.domain.model.effect.keyword.charge.ChargeEffect;
import com.saroga.timewarp.domain.model.effect.effectInterface.Effect;
import com.saroga.timewarp.domain.model.effect.keyword.glory.GloryEffect;
import com.saroga.timewarp.domain.model.entity.card.Card;
import com.saroga.timewarp.domain.model.entity.gameState.GameState;
import com.saroga.timewarp.domain.model.entity.playerState.PlayerState;
import com.saroga.timewarp.domain.model.enums.CardLocation;
import com.saroga.timewarp.domain.model.enums.CardType;
import com.saroga.timewarp.domain.model.vo.card.Cost;
import com.saroga.timewarp.domain.model.vo.card.Drive;
import com.saroga.timewarp.domain.service.CombatService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class GameFlowTest {
    @Test
    void should_ExecuteCompleteRoundFlow_WithCombatAndScoring() {
        // --- 1. CONFIGURACIÓN (Arrange) ---
        CombatService combatService = new CombatService();
        UUID idA = UUID.randomUUID();
        UUID idB = UUID.randomUUID();

        // Efectos: Charge (+1 si hay enemigos) y Glory (Ready al ganar)
        Effect charge = new ChargeEffect();
        Effect glory = new GloryEffect();

        // Caballero de A (5/5)
        Card caballeroA = new Card(UUID.randomUUID(), idA, "Caballero", Drive.of(5), Drive.of(5),
                Cost.of(3), CardType.UNIT, new ArrayList<>(), List.of(charge, glory));

        // Orco de B (2/2) - Un "minion" para que el Caballero cargue
        Card orcoB = new Card(UUID.randomUUID(), idB, "Orco", Drive.of(2), Drive.of(2),
                Cost.of(1), CardType.UNIT, new ArrayList<>(), new ArrayList<>());

        PlayerState pA = new PlayerState(idA, new ArrayList<>(), true);
        PlayerState pB = new PlayerState(idB, new ArrayList<>(), false);
        GameState game = new GameState(UUID.randomUUID(), pA, pB);

        // Empezamos el Turno 1
        game.newTurn();

        // --- 2. ACCIONES (Act) ---

        // Paso A: El Orco de B ya está en el campo izquierdo (simulamos que entró antes)
        pB.addCardToLocation(orcoB, CardLocation.FIELD_LEFT);
        orcoB.setCardLocation(CardLocation.FIELD_LEFT);
        orcoB.setCardLocationAtBeginningOfRound(CardLocation.FIELD_LEFT);

        // Paso B: El Caballero de A está en la Base y se mueve al campo izquierdo
        pA.addCardToLocation(caballeroA, CardLocation.MY_BASE);
        caballeroA.setCardLocation(CardLocation.MY_BASE);
        caballeroA.setCardLocationAtBeginningOfRound(CardLocation.MY_BASE);
        caballeroA.ready(); // Lo despertamos para que pueda moverse

        // Acción: Movimiento manual
        pA.moveCard(caballeroA, CardLocation.FIELD_LEFT);

        // Acción: CONFIRMACIÓN (Aquí es donde ocurre la magia técnica)
        // Se dispara el Charge porque el OrcoB está en FIELD_LEFT
        pA.confirmMoveAndPassPriority(game);

        // --- 3. VERIFICACIONES INTERMEDIAS (Asserts) ---
        assertEquals(6, caballeroA.getDrive().value(), "El Caballero debe tener 6 de Drive por el Charge");
        assertTrue(caballeroA.isExhausted(), "Debe estar agotado tras confirmar el movimiento");

        // --- 4. RESOLUCIÓN DE COMBATE ---
        // Caballero (6) vs Orco (2)
        // Orco muere. Caballero recibe 2 de daño (6-2 = 4).
        combatService.resolveLaneCombat(game, CardLocation.FIELD_LEFT);

        // Verificaciones tras combate
        assertTrue(orcoB.isDestroyed(), "El Orco debería haber muerto");
        assertEquals(4, caballeroA.getDrive().value(), "El Caballero debería tener 4 de vida");
        assertFalse(caballeroA.isExhausted(), "¡GLORY! El Caballero debe estar Ready por ganar");

        //mientras no se confirme nada el caballero permanece sin estar exhausto.
        pA.moveCard(caballeroA, CardLocation.MY_BASE);
        assertEquals(CardLocation.MY_BASE, caballeroA.getCardLocation());
        assertFalse(caballeroA.isExhausted());
        //fuerzo prioridad para poder confirmar
        pA.setPriority(true);
        pA.confirmMoveAndPassPriority(game);
        assertTrue(caballeroA.isExhausted());

        // --- 5. CAMBIO DE RONDA Y PUNTUACIÓN ---
        // Al llamar a newTurn, se ejecuta updateScores().
        // Como A tiene una unidad en FIELD_LEFT y B ninguna, A gana 1 punto.
        game.newTurn();

        // --- 6. VERIFICACIONES FINALES ---
        assertEquals(0, pA.getScore().value(), "Jugador A debería tener 1 punto");
        assertEquals(0, pB.getScore().value(), "Jugador B debería tener 0 puntos");

        // Verificación de reseteo de Snapshot
        assertEquals(CardLocation.MY_BASE, caballeroA.getCardLocationAtBeginningOfRound(),
                "La ubicación inicial para la nueva ronda debe ser el campo izquierdo");
    }
}
