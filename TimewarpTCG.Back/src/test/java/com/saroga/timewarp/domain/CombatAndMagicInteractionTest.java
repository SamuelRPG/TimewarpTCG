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

public class CombatAndMagicInteractionTest {

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

        List<TargetingRequirement> fastSpellReqs = List.of(new TargetingRequirement(1, TargetGroup.ENEMY));
        List<TargetingRequirement> instantSpellReqs = List.of(new TargetingRequirement(1, TargetGroup.ALLY));

        List<Effect> buffEffect = List.of(new AllyBoostEffect(Drive.of(2)));
        List<Effect> damageEffect = List.of(new EnemyDamageDealtEffect(Drive.of(2)));

        Card unitA = Card.createUnit(uuidA, "Guerrero A", Drive.of(3), Cost.of(1),
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>());

        Card unitB = Card.createUnit(uuidB, "Muro B", Drive.of(4), Cost.of(1),
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>());

        Card instantSpell = Card.createSpell(uuidA, "Fuerza Bruta", Cost.of(1),
                buffEffect, instantSpellReqs, SpellSpeed.INSTANT);

        Card fastSpell = Card.createSpell(uuidB, "Rayo Debilitador", Cost.of(1),
                damageEffect, fastSpellReqs, SpellSpeed.FAST);

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
        pA.drawCard();
        Card unitA = pA.getHand().getFirst();
        cardPlayService.executePlay(gameState, uuidA, unitA, CardLocation.MY_BASE, new ArrayList<>());
        pA.readyAllCards();

        pB.drawCard();
        Card unitB = pB.getHand().getFirst();
        cardPlayService.executePlay(gameState, uuidB, unitB, CardLocation.MY_BASE, new ArrayList<>());
        pB.readyAllCards();

        pA.moveCard(unitA, CardLocation.FIELD_LEFT);
        gameFlowService.confirmMoveAndPassPriority(gameState, uuidA, combatService); // <-- CAMBIO AQUÍ

        pB.moveCard(unitB, CardLocation.FIELD_LEFT);
        gameFlowService.confirmMoveAndPassPriority(gameState, uuidB, combatService); // <-- CAMBIO AQUÍ

        assertEquals(GamePhase.COMBAT_OPEN, gameState.getGamePhase());

        gameFlowService.confirmMoveAndPassPriority(gameState, uuidA, combatService); // <-- CAMBIO AQUÍ

        pB.drawCard();
        Card fastSpell = pB.getHand().getFirst();
        cardPlayService.executePlay(gameState, uuidB, fastSpell, CardLocation.FIELD_LEFT, List.of(unitA));

        assertEquals(3, unitA.getDrive().value());

        pA.drawCard();
        Card instantSpell = pA.getHand().getFirst();
        cardPlayService.executePlay(gameState, uuidA, instantSpell, CardLocation.FIELD_LEFT, List.of(unitA));

        assertEquals(5, unitA.getDrive().value());

        gameFlowService.confirmMoveAndPassPriority(gameState, uuidA, combatService); // <-- CAMBIO AQUÍ
        gameFlowService.confirmMoveAndPassPriority(gameState, uuidB, combatService); // <-- CAMBIO AQUÍ

        assertTrue(gameState.isStackEmpty());
        assertEquals(3, unitA.getDrive().value());

        gameFlowService.confirmMoveAndPassPriority(gameState, uuidA, combatService); // <-- CAMBIO AQUÍ
        gameFlowService.confirmMoveAndPassPriority(gameState, uuidB, combatService); // <-- CAMBIO AQUÍ

        assertEquals(GamePhase.MAIN_OPEN, gameState.getGamePhase());

        assertTrue(pB.getFieldLeft().contains(unitB));
        assertFalse(unitB.isDestroyed());
        assertEquals(1, unitB.getDrive().value(), "El Muro B debería sobrevivir con 1 de Drive");

        assertFalse(pA.getFieldLeft().contains(unitA), "El Guerrero A debería haber desaparecido del campo");
        assertTrue(unitA.isDestroyed(), "La carta del Guerrero A debería estar marcada como destruida");
        assertTrue(pA.getGraveyard().contains(unitA), "El Guerrero A debería estar en el cementerio");
    }
}