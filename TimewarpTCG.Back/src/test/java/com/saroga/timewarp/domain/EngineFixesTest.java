package com.saroga.timewarp.domain;

import com.saroga.timewarp.domain.model.effect.action.drawCard.DrawCardAction;
import com.saroga.timewarp.domain.model.effect.action.enemyDamage.EnemyDamageAction;
import com.saroga.timewarp.domain.model.effect.effectInterface.Effect;
import com.saroga.timewarp.domain.model.effect.triggered.TriggeredEffect;
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

/** Fija los arreglos del motor: unidades muertas por hechizo, SLOW fuera de fase y acción DRAW. */
public class EngineFixesTest {

    private UUID a, b;
    private PlayerState pA, pB;
    private GameState game;
    private final CardPlayService play = new CardPlayService();
    private final GameFlowService flow = new GameFlowService();
    private final CombatService combat = new CombatService();

    @BeforeEach
    void setUp() {
        a = UUID.randomUUID();
        b = UUID.randomUUID();
        pA = new PlayerState(a, new ArrayList<>(), true);
        pB = new PlayerState(b, new ArrayList<>(), false);
        pA.setResource(Resource.of(10));
        pB.setResource(Resource.of(10));
        game = new GameState(UUID.randomUUID(), pA, pB);
    }

    private Card unitOnField(PlayerState owner, UUID id, String name, int drive, CardLocation loc) {
        Card c = Card.createUnit(id, name, "", Drive.of(drive), Cost.of(1), List.of(), List.of(), List.of(), Faction.ROMANS);
        owner.addCardToLocation(c, loc);
        c.setCardLocation(loc);
        return c;
    }

    private Card spell(UUID owner, SpellSpeed speed, int dmg, List<TargetingRequirement> reqs) {
        List<Effect> fx = List.of(new TriggeredEffect("ON_PLACED", new EnemyDamageAction(Drive.of(dmg))));
        Card s = Card.createSpell(owner, "Hechizo", "", Cost.of(1), fx, reqs, speed, Faction.ROMANS);
        s.setCardLocation(CardLocation.HAND);
        (owner.equals(a) ? pA : pB).getHand().add(s);
        return s;
    }

    @Test
    void instantSpellThatKillsUnitRemovesItFromBoard() {
        Card victim = unitOnField(pB, b, "Victima", 2, CardLocation.FIELD_LEFT);
        Card s = spell(a, SpellSpeed.INSTANT, 5, List.of(new TargetingRequirement(1, TargetGroup.ENEMY)));

        play.executePlay(game, a, s, CardLocation.MY_BASE, List.of(victim));

        assertTrue(victim.isDestroyed());
        assertFalse(pB.getFieldLeft().contains(victim), "la unidad muerta no debe seguir en el tablero");
        assertTrue(pB.getGraveyard().contains(victim), "debe ir al cementerio");
        assertFalse(pB.hasUnitsIn(CardLocation.FIELD_LEFT), "el carril ya no cuenta para puntuar ni disputar");
        assertEquals(CardLocation.GRAVEYARD, victim.getCardLocation());
    }

    @Test
    void fastSpellOnTheStackRemovesKilledUnitWhenResolved() {
        Card victim = unitOnField(pB, b, "Victima", 3, CardLocation.MY_BASE);
        Card s = spell(a, SpellSpeed.FAST, 3, List.of(new TargetingRequirement(1, TargetGroup.ENEMY)));

        play.executePlay(game, a, s, CardLocation.MY_BASE, List.of(victim));
        assertFalse(game.isStackEmpty());
        flow.confirmMoveAndPassPriority(game, b, combat);   // B pasa
        flow.confirmMoveAndPassPriority(game, a, combat);   // A pasa -> se resuelve la pila

        assertTrue(game.isStackEmpty());
        assertFalse(pB.getBase().contains(victim));
        assertTrue(pB.getGraveyard().contains(victim));
    }

    @Test
    void slowSpellOutsideMainOpenIsRejectedWithoutSideEffects() {
        game.setGamePhase(GamePhase.COMBAT_OPEN);
        Card s = spell(a, SpellSpeed.SLOW, 1, List.of());
        int resourceBefore = pA.getResource().value();

        assertThrows(IllegalStateException.class, () -> play.executePlay(game, a, s, CardLocation.MY_BASE, new ArrayList<>()));

        assertEquals(resourceBefore, pA.getResource().value(), "no se debe gastar recurso");
        assertTrue(pA.getHand().contains(s), "la carta debe seguir en la mano");
        assertTrue(game.isStackEmpty(), "no debe quedar nada en la pila");
    }

    @Test
    void slowSpellInMainOpenStillWorks() {
        Card s = spell(a, SpellSpeed.SLOW, 1, List.of());
        play.executePlay(game, a, s, CardLocation.MY_BASE, new ArrayList<>());
        assertEquals(1, game.getSpellStack().size());
        assertEquals(GamePhase.MAIN_CLOSED, game.getGamePhase());
    }

    @Test
    void drawActionDrawsCardsForTheOwner() {
        for (int i = 0; i < 5; i++) {
            Card c = Card.createUnit(a, "Mazo" + i, "", Drive.of(1), Cost.of(1), List.of(), List.of(), List.of(), Faction.ROMANS);
            pA.getDeck().add(c);
        }
        Card src = unitOnField(pA, a, "Fuente", 1, CardLocation.MY_BASE);

        new DrawCardAction(2).execute(game, src, new ArrayList<>());

        assertEquals(2, pA.getHand().size());
        assertEquals(3, pA.getDeck().size());
        assertEquals(0, pB.getHand().size(), "solo roba el dueño");
    }

    @Test
    void drawActionWithEmptyDeckDoesNothing() {
        Card src = unitOnField(pA, a, "Fuente", 1, CardLocation.MY_BASE);
        new DrawCardAction(3).execute(game, src, new ArrayList<>());
        assertEquals(0, pA.getHand().size());
    }
}
