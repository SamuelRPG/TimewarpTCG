package com.saroga.timewarp.domain;

import com.saroga.timewarp.application.factory.EffectFactory;
import com.saroga.timewarp.application.factory.TargetingFactory;
import com.saroga.timewarp.domain.model.entity.card.Card;
import com.saroga.timewarp.domain.model.entity.cardTemplate.CardTemplate;
import com.saroga.timewarp.domain.model.entity.gameState.GameState;
import com.saroga.timewarp.domain.model.entity.playerState.PlayerState;
import com.saroga.timewarp.domain.model.enums.*;
import com.saroga.timewarp.domain.model.vo.playerState.Resource;
import com.saroga.timewarp.domain.service.CardPlayService;
import com.saroga.timewarp.domain.service.CombatService;
import com.saroga.timewarp.domain.service.GameFlowService;
import com.saroga.timewarp.infrastructure.persistence.jpa.entity.CardTemplate.CardTemplateEntity;
import com.saroga.timewarp.infrastructure.persistence.jpa.mapper.CardTemplateMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class TriggeredEffectsIntegrationTest {

    private CardPlayService cardPlayService;
    private GameFlowService gameFlowService;
    private CombatService combatService;
    private CardTemplateMapper mapper;

    @BeforeEach
    void setUp() {
        // 1. Inicializamos los servicios del Dominio
        cardPlayService = new CardPlayService();
        gameFlowService = new GameFlowService();
        combatService = new CombatService();

        // 2. Inicializamos la Infraestructura (Fábricas y Mapper)
        EffectFactory effectFactory = new EffectFactory();
        TargetingFactory targetingFactory = new TargetingFactory(); // <-- NUESTRA NUEVA FÁBRICA
        mapper = new CardTemplateMapper(effectFactory, targetingFactory); // <-- AHORA RECIBE DOS FÁBRICAS
    }

    @Test
    void testEffectsPipelineFromDbToCombat() {
        UUID playerAId = UUID.randomUUID();
        UUID playerBId = UUID.randomUUID();

        // =====================================================================
        // FASE 1: SIMULAMOS LO QUE DEVUELVE LA BASE DE DATOS (H2)
        // =====================================================================

        // Carta 1: Un clérigo que al entrar da +2 de Drive a un aliado
        CardTemplateEntity entityCleric = new CardTemplateEntity();
        entityCleric.setId("UNIT_CLERIC");
        entityCleric.setName("Clérigo de Batalla");
        entityCleric.setBaseDrive(2);
        entityCleric.setBaseCost(1);
        entityCleric.setCardType("UNIT");
        entityCleric.setSpellSpeed("NOT_A_SPELL");
        entityCleric.setTargets("1:ALLY"); // <-- NUEVO FORMATO DE TARGETS
        entityCleric.setKeywords("NONE");
        entityCleric.setEffects("ON_PLACED:ALLY_BOOST:2");
        entityCleric.setFaction("ANCIENT_EGYPT");

        // Carta 2: Un Ogro que al recibir daño se enfurece (+1 Drive)
        CardTemplateEntity entityOgre = new CardTemplateEntity();
        entityOgre.setId("UNIT_OGRE");
        entityOgre.setName("Ogro Furioso");
        entityOgre.setBaseDrive(5);
        entityOgre.setBaseCost(1);
        entityOgre.setCardType("UNIT");
        entityOgre.setSpellSpeed("NOT_A_SPELL");
        entityOgre.setTargets("NONE"); // <-- NUEVO FORMATO DE TARGETS
        entityOgre.setKeywords("NONE");
        entityOgre.setEffects("ON_DAMAGE_TAKEN:SELF_BUFF:1");
        entityOgre.setFaction("ANCIENT_EGYPT");

        // Carta 3: Un hechizo rápido enemigo para pegarle al Ogro y probar su efecto
        CardTemplateEntity entitySpell = new CardTemplateEntity();
        entitySpell.setId("SPELL_FIRE");
        entitySpell.setName("Bola de Fuego");
        entitySpell.setBaseDrive(0);
        entitySpell.setBaseCost(1);
        entitySpell.setCardType("SPELL");
        entitySpell.setSpellSpeed("FAST");
        entitySpell.setTargets("1:ENEMY"); // <-- NUEVO FORMATO DE TARGETS
        entitySpell.setKeywords("NONE");
        entitySpell.setEffects("ON_PLACED:ENEMY_DAMAGE:2");
        entitySpell.setFaction("ANCIENT_EGYPT");

        // =====================================================================
        // FASE 2: EL MAPPER TRADUCE EL TEXTO A REGLAS DE JUEGO (PLANTILLAS)
        // =====================================================================
        CardTemplate templateCleric = mapper.toDomain(entityCleric);
        CardTemplate templateOgre = mapper.toDomain(entityOgre);
        CardTemplate templateSpell = mapper.toDomain(entitySpell);

        // Verificamos que las fábricas hicieron bien su trabajo
        assertEquals(1, templateCleric.getEffects().size(), "El Clérigo debería tener 1 efecto mapeado");
        assertEquals(1, templateOgre.getEffects().size(), "El Ogro debería tener 1 efecto mapeado");
        assertEquals(1, templateCleric.getTargetingRequirements().size(), "El Clérigo debería pedir 1 objetivo aliado");

        // =====================================================================
        // FASE 3: CREAMOS CARTAS REALES (Simulando instantiateFromTemplate)
        // =====================================================================

        // <-- AHORA PASAMOS EL ENUM DIRECTAMENTE, NADA DE STRINGS RAROS
        Card clericCard = new Card(UUID.randomUUID(), playerAId, templateCleric.getName(), templateCleric.getDescription(),
                templateCleric.getBaseDrive(), templateCleric.getBaseDrive(), templateCleric.getBaseCost(),
                templateCleric.getCardType(), templateCleric.getKeywords(),
                templateCleric.getEffects(), templateCleric.getTargetingRequirements(), templateCleric.getSpellSpeed(), templateCleric.getFaction());

        Card ogreCard = new Card(UUID.randomUUID(), playerAId, templateOgre.getName(), templateOgre.getDescription(),
                templateOgre.getBaseDrive(), templateOgre.getBaseDrive(), templateOgre.getBaseCost(),
                templateOgre.getCardType(), templateOgre.getKeywords(),
                templateOgre.getEffects(), templateOgre.getTargetingRequirements(), templateOgre.getSpellSpeed(), templateOgre.getFaction());

        Card spellCard = new Card(UUID.randomUUID(), playerBId, templateSpell.getName(), templateSpell.getDescription(),
                templateSpell.getBaseDrive(), templateSpell.getBaseDrive(), templateSpell.getBaseCost(),
                templateSpell.getCardType(), templateSpell.getKeywords(),
                templateSpell.getEffects(), templateSpell.getTargetingRequirements(), templateSpell.getSpellSpeed(), templateSpell.getFaction());

        // Ponemos todas las cartas en la mano para empezar a jugar
        clericCard.setCardLocation(CardLocation.HAND);
        ogreCard.setCardLocation(CardLocation.HAND);
        spellCard.setCardLocation(CardLocation.HAND);

        // =====================================================================
        // FASE 4: PREPARAMOS LA MESA DE JUEGO
        // =====================================================================
        PlayerState pA = new PlayerState(playerAId, new ArrayList<>(), true);
        PlayerState pB = new PlayerState(playerBId, new ArrayList<>(), false);
        pA.setResource(Resource.of(10));
        pB.setResource(Resource.of(10));

        pA.getHand().add(ogreCard);
        pA.getHand().add(clericCard);
        pB.getHand().add(spellCard);

        GameState game = new GameState(UUID.randomUUID(), pA, pB);

        // =====================================================================
        // FASE 5: JUGAMOS LA PARTIDA Y COMPROBAMOS LOS TRIGGERS
        // =====================================================================

        // 1. El Jugador A baja al Ogro (No tiene targets)
        cardPlayService.executePlay(game, playerAId, ogreCard, CardLocation.MY_BASE, new ArrayList<>());
        assertTrue(pA.getBase().contains(ogreCard));
        assertEquals(5, ogreCard.getDrive().value(), "El Ogro empieza con 5 de vida");

        // (Cedió prioridad, B la devuelve a A)
        gameFlowService.confirmMoveAndPassPriority(game, playerBId, combatService);

        // 2. El Jugador A baja al Clérigo, TARGETEANDO AL OGRO (Trigger: ON_PLACED)
        cardPlayService.executePlay(game, playerAId, clericCard, CardLocation.MY_BASE, List.of(ogreCard));

        // El efecto "al entrar en juego" va ahora a la PILA: se resuelve cuando los dos pasan seguidos (B responde pasando, luego A).
        assertEquals(5, ogreCard.getDrive().value(), "el efecto del Clérigo aún está en la pila");
        gameFlowService.confirmMoveAndPassPriority(game, playerBId, combatService);
        gameFlowService.confirmMoveAndPassPriority(game, playerAId, combatService);
        // ¡VERIFICACIÓN CRÍTICA DEL TRIGGER ON_PLACED!
        assertEquals(7, ogreCard.getDrive().value(), "¡EL TRIGGER ON_PLACED FALLÓ! El Ogro debió recibir el buff del Clérigo");
        System.out.println("✅ Éxito: Trigger ON_PLACED -> " + ogreCard.getName() + " tiene ahora " + ogreCard.getDrive().value() + " de Drive.");

        // ---> B devuelve el turno para que podamos mover
        gameFlowService.confirmMoveAndPassPriority(game, playerBId, combatService);

        // 3. Movemos al Ogro a un Field para poder tirarle el hechizo
        ogreCard.ready();
        pA.moveCard(ogreCard, CardLocation.FIELD_LEFT);
        gameFlowService.confirmMoveAndPassPriority(game, playerAId, combatService);

        // 4. El Jugador B le tira la Bola de Fuego al Ogro (Daño 2)
        cardPlayService.executePlay(game, playerBId, spellCard, CardLocation.FIELD_LEFT, List.of(ogreCard));
        // Se resuelve la pila (pasamos ambos)
        gameFlowService.confirmMoveAndPassPriority(game, playerAId, combatService);
        gameFlowService.confirmMoveAndPassPriority(game, playerBId, combatService);

        // ¡VERIFICACIÓN CRÍTICA DEL TRIGGER ON_DAMAGE_TAKEN!
        // El Ogro tenía 7. La bola le quita 2 (baja a 5).
        // Al recibir daño y sobrevivir, su efecto "ON_DAMAGE_TAKEN:SELF_BUFF:1" salta. Sube de 5 a 6.
        assertEquals(6, ogreCard.getDrive().value(), "¡EL TRIGGER ON_DAMAGE_TAKEN FALLÓ! El Ogro debió curarse 1 al sobrevivir.");
        System.out.println("✅ Éxito: Trigger ON_DAMAGE_TAKEN -> " + ogreCard.getName() + " sobrevivió y se enfureció, quedando en " + ogreCard.getDrive().value() + " de Drive.");
    }
}