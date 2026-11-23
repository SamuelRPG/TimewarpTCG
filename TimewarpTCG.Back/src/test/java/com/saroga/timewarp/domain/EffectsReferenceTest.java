package com.saroga.timewarp.domain;

import com.saroga.timewarp.application.factory.EffectFactory;
import com.saroga.timewarp.application.factory.TargetingFactory;
import com.saroga.timewarp.domain.model.effect.action.allyBoost.AllyBoostAction;
import com.saroga.timewarp.domain.model.entity.card.Card;
import com.saroga.timewarp.domain.model.entity.gameState.GameState;
import com.saroga.timewarp.domain.model.entity.playerState.PlayerState;
import com.saroga.timewarp.domain.model.enums.*;
import com.saroga.timewarp.domain.model.vo.card.Cost;
import com.saroga.timewarp.domain.model.vo.card.Drive;
import com.saroga.timewarp.domain.model.vo.playerState.Resource;
import com.saroga.timewarp.domain.service.CardPlayService;
import com.saroga.timewarp.domain.service.CombatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Referencia ejecutable de lo que hace cada efecto (ver docs/03_EFECTOS_KEYWORDS_Y_BASE_DE_DATOS.md): cada prueba
 * construye la carta con las MISMAS cadenas que se guardan en la columna "effects" / "targets" de Supabase.
 */
public class EffectsReferenceTest {

    private UUID a, b;
    private PlayerState pA, pB;
    private GameState game;
    private final CardPlayService play = new CardPlayService();
    private final CombatService combat = new CombatService();
    private final EffectFactory effects = new EffectFactory();
    private final TargetingFactory targeting = new TargetingFactory();

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

    /** Unidad ya en juego (como si se hubiera bajado antes). */
    private Card onBoard(PlayerState owner, UUID id, int drive, CardLocation loc, String fx, String targets, Keyword... kw) {
        Card c = Card.createUnit(id, "U", "", Drive.of(drive), Cost.of(1), List.of(kw),
                effects.createEffects(fx), targeting.createTargeting(targets), Faction.ROMANS);
        owner.addCardToLocation(c, loc);
        c.setCardLocation(loc);
        return c;
    }

    private Card inHand(PlayerState owner, UUID id, int drive, String fx, String targets) {
        Card c = Card.createUnit(id, "U", "", Drive.of(drive), Cost.of(1), List.of(),
                effects.createEffects(fx), targeting.createTargeting(targets), Faction.ROMANS);
        c.setCardLocation(CardLocation.HAND);
        owner.getHand().add(c);
        return c;
    }

    private Card spellInHand(PlayerState owner, SpellSpeed speed, String fx, String targets) {
        Card s = Card.createSpell(owner.getPlayerId(), "H", "", Cost.of(1), effects.createEffects(fx),
                targeting.createTargeting(targets), speed, Faction.ROMANS);
        s.setCardLocation(CardLocation.HAND);
        owner.getHand().add(s);
        return s;
    }

    // ------------------------------------------------------------------ objetivos de las UNIDADES (ON_PLACED)

    @Test
    void unitCannotTakeMoreTargetsThanItAsksFor() {
        Card e1 = onBoard(pB, b, 5, CardLocation.FIELD_LEFT, "NONE", "NONE");
        Card e2 = onBoard(pB, b, 5, CardLocation.FIELD_LEFT, "NONE", "NONE");
        Card tribune = inHand(pA, a, 5, "ON_PLACED:ENEMY_DAMAGE:2", "1:ENEMY");

        assertThrows(IllegalStateException.class, () -> play.executePlay(game, a, tribune, CardLocation.MY_BASE, List.of(e1, e2)),
                "un cliente modificado no puede llevarse 2 objetivos si la carta pide 1");
        assertEquals(5, e1.getDrive().value());
        assertEquals(5, e2.getDrive().value());
        assertEquals(CardLocation.HAND, tribune.getCardLocation(), "la carta no debe haberse jugado");
        assertEquals(10, pA.getResource().value(), "ni gastado recurso");

        play.executePlay(game, a, tribune, CardLocation.MY_BASE, List.of(e1));
        assertEquals(3, e1.getDrive().value(), "el objetivo legitimo recibe el dano");
        assertEquals(5, e2.getDrive().value());
    }

    @Test
    void unitCannotTargetCardsThatAreNotInPlay() {
        Card handCard = inHand(pA, a, 3, "NONE", "NONE");   // una carta tuya que aun no has jugado
        Card priest = inHand(pA, a, 3, "ON_PLACED:ALLY_BOOST:2", "1:ALLY");

        assertThrows(IllegalStateException.class, () -> play.executePlay(game, a, priest, CardLocation.MY_BASE, List.of(handCard)));
        assertEquals(3, handCard.getDrive().value(), "no se puede reforzar una carta de la mano");
    }

    @Test
    void unitTargetsMustMatchTheGroupAndNotRepeat() {
        Card ally = onBoard(pA, a, 3, CardLocation.MY_BASE, "NONE", "NONE");
        Card enemy = onBoard(pB, b, 3, CardLocation.FIELD_LEFT, "NONE", "NONE");
        Card hunter = inHand(pA, a, 3, "ON_PLACED:HUNTER_MARK:1:5", "1:ENEMY");
        assertThrows(IllegalStateException.class, () -> play.executePlay(game, a, hunter, CardLocation.MY_BASE, List.of(ally)),
                "pide un ENEMY y le das un aliado");

        Card pharaoh = inHand(pA, a, 7, "ON_PLACED:ALLY_BOOST:1", "2:ALLY");
        Card ally2 = onBoard(pA, a, 3, CardLocation.MY_BASE, "NONE", "NONE");
        assertThrows(IllegalStateException.class, () -> play.executePlay(game, a, pharaoh, CardLocation.MY_BASE, List.of(ally2, ally2)),
                "la misma unidad dos veces");
        assertEquals(3, enemy.getDrive().value());
    }

    @Test
    void unitMayChooseFewerTargetsOrNone_theEffectJustDoesNothing() {
        Card enemy = onBoard(pB, b, 4, CardLocation.FIELD_LEFT, "NONE", "NONE");
        Card tribune = inHand(pA, a, 5, "ON_PLACED:ENEMY_DAMAGE:2", "1:ENEMY");

        play.executePlay(game, a, tribune, CardLocation.MY_BASE, List.of());
        assertEquals(CardLocation.MY_BASE, tribune.getCardLocation());
        assertEquals(4, enemy.getDrive().value());
    }

    @Test
    void unitWithoutTargetsInItsRecipeRejectsAnyTarget() {
        Card enemy = onBoard(pB, b, 4, CardLocation.FIELD_LEFT, "NONE", "NONE");
        Card legionary = inHand(pA, a, 3, "NONE", "NONE");
        assertThrows(IllegalStateException.class, () -> play.executePlay(game, a, legionary, CardLocation.MY_BASE, List.of(enemy)));
    }

    @Test
    void spellCannotTargetCardsInHand() {
        Card handCard = inHand(pA, a, 3, "NONE", "NONE");
        Card blessing = spellInHand(pA, SpellSpeed.INSTANT, "ON_PLACED:ALLY_BOOST:3", "1:ALLY");

        assertThrows(IllegalStateException.class, () -> play.executePlay(game, a, blessing, CardLocation.MY_BASE, List.of(handCard)));
        assertEquals(3, handCard.getDrive().value());
        assertEquals(10, pA.getResource().value());
    }

    // ------------------------------------------------------------------ acciones

    @Test
    void damageIsPermanent_driveIsBothAttackAndHealth() {
        Card enemy = onBoard(pB, b, 5, CardLocation.FIELD_LEFT, "NONE", "NONE");
        Card jav = spellInHand(pA, SpellSpeed.INSTANT, "ON_PLACED:ENEMY_DAMAGE:2", "1:ENEMY");
        play.executePlay(game, a, jav, CardLocation.MY_BASE, List.of(enemy));
        assertEquals(3, enemy.getDrive().value(), "el dano reduce el Drive (que tambien es la fuerza de ataque) y no se cura");
    }

    @Test
    void survivingDamageTriggersSelfBuff() {
        Card guardian = onBoard(pB, b, 5, CardLocation.FIELD_LEFT, "ON_DAMAGE_TAKEN:SELF_BUFF:1", "NONE");
        guardian.takeDamage(game, Drive.of(2));
        assertEquals(4, guardian.getDrive().value(), "5 - 2 + 1");
        assertTrue(guardian.isAlive());
    }

    @Test
    void lethalDamageIsNotUndoneByTheDamageTakenTrigger() {
        // "Si sobrevive al daño, gana +2": con daño letal NO sobrevive, asi que no debe ganar nada ni "resucitar"
        Card cyborg = onBoard(pB, b, 3, CardLocation.FIELD_LEFT, "ON_DAMAGE_TAKEN:SELF_BUFF:2", "NONE");
        cyborg.takeDamage(game, Drive.of(10));
        assertTrue(cyborg.isDestroyed());
        assertEquals(0, cyborg.getDrive().value());
    }

    @Test
    void allyBoostIgnoresEnemyTargets() {
        Card enemy = onBoard(pB, b, 3, CardLocation.FIELD_LEFT, "NONE", "NONE");
        Card ally = onBoard(pA, a, 3, CardLocation.MY_BASE, "NONE", "NONE");
        new AllyBoostAction(Drive.of(2)).execute(game, ally, List.of(enemy, ally));
        assertEquals(3, enemy.getDrive().value());
        assertEquals(5, ally.getDrive().value());
    }

    @Test
    void hunterMark_instantKillPaysTheRewardAtOnce() {
        Card prey = onBoard(pB, b, 1, CardLocation.FIELD_LEFT, "NONE", "NONE");
        Card hunter = inHand(pA, a, 3, "ON_PLACED:HUNTER_MARK:1:5", "1:ENEMY");
        play.executePlay(game, a, hunter, CardLocation.MY_BASE, List.of(prey));
        assertTrue(prey.isDestroyed());
        assertEquals(8, hunter.getDrive().value(), "3 + 5");
    }

    @Test
    void hunterMark_survivingPreyIsMarkedAndPaysWhenItDiesLater() {
        Card prey = onBoard(pB, b, 4, CardLocation.FIELD_LEFT, "NONE", "NONE");
        Card hunter = inHand(pA, a, 3, "ON_PLACED:HUNTER_MARK:1:5", "1:ENEMY");
        play.executePlay(game, a, hunter, CardLocation.MY_BASE, List.of(prey));
        assertEquals(3, prey.getDrive().value());
        assertEquals(3, hunter.getDrive().value(), "todavia no hay recompensa");

        prey.takeDamage(game, Drive.of(99));     // muere mas tarde, por lo que sea (combate, hechizo...)
        assertEquals(8, hunter.getDrive().value(), "ahora si: +5");
    }

    @Test
    void chargeBuff_onlyWhenTheMovementEndsInALane() {
        Card car = onBoard(pA, a, 4, CardLocation.FIELD_LEFT, "ON_MOVEMENT_CONFIRMED:CHARGE_BUFF:1", "NONE", Keyword.JUMPER);
        car.onMovementConfirmed(game, pA, pB, CardLocation.FIELD_LEFT);
        assertEquals(5, car.getDrive().value(), "llega a un carril: +1 (permanente, no solo durante el combate)");

        car.setCardLocation(CardLocation.MY_BASE);
        car.onMovementConfirmed(game, pA, pB, CardLocation.MY_BASE);
        assertEquals(5, car.getDrive().value(), "volver a la base no da nada");
    }

    @Test
    void readySelf_afterWinningACombat_theUnitIsReadyAgain() {
        Card gladiator = onBoard(pA, a, 5, CardLocation.FIELD_LEFT, "ON_COMBAT_WIN:READY_SELF", "NONE");
        onBoard(pB, b, 2, CardLocation.FIELD_LEFT, "NONE", "NONE");
        gladiator.exhaust();
        assertTrue(gladiator.isExhausted());

        combat.resolveLaneCombat(game, CardLocation.FIELD_LEFT);
        assertFalse(gladiator.isExhausted(), "Gloria: gana el combate y se recupera");
    }

    @Test
    void selfBuffOnCombatWin_onlyTheWinnerGrows() {
        Card mummy = onBoard(pA, a, 5, CardLocation.FIELD_LEFT, "ON_COMBAT_WIN:SELF_BUFF:2", "NONE");
        Card loser = onBoard(pB, b, 2, CardLocation.FIELD_LEFT, "ON_COMBAT_WIN:SELF_BUFF:2", "NONE");
        combat.resolveLaneCombat(game, CardLocation.FIELD_LEFT);
        // el combate hace dano a los dos bandos a la vez: la Momia (5) recibe 2 del rival y, al ganar, suma +2
        assertEquals(5 - 2 + 2, mummy.getDrive().value());
        assertTrue(loser.isDestroyed(), "el perdedor muere y no recibe su ON_COMBAT_WIN");
    }
}
