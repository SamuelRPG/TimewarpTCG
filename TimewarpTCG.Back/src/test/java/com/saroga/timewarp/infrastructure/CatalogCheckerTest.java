package com.saroga.timewarp.infrastructure;

import com.saroga.timewarp.application.factory.EffectFactory;
import com.saroga.timewarp.application.factory.TargetingFactory;
import com.saroga.timewarp.infrastructure.persistence.jpa.CatalogChecker;
import com.saroga.timewarp.infrastructure.persistence.jpa.entity.CardTemplate.CardTemplateEntity;
import com.saroga.timewarp.infrastructure.persistence.jpa.mapper.CardTemplateMapper;
import com.saroga.timewarp.infrastructure.persistence.jpa.repository.CardTemplateRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** El validador del catalogo: el catalogo real debe salir limpio y los typos habituales deben detectarse. */
@SpringBootTest(properties = "timewarp.security.jwt-secret=una-clave-de-pruebas-de-mas-de-32-caracteres!!")
public class CatalogCheckerTest {

    @Autowired CardTemplateRepository repository;
    @Autowired CardTemplateMapper mapper;

    private static CardTemplateEntity card(String type, String speed, String targets, String keywords, String effects) {
        CardTemplateEntity c = new CardTemplateEntity();
        c.setId("T_01"); c.setName("Prueba"); c.setDescription("");
        c.setBaseCost(2); c.setBaseDrive("UNIT".equals(type) ? 3 : 0);
        c.setCardType(type); c.setSpellSpeed(speed); c.setTargets(targets); c.setKeywords(keywords); c.setEffects(effects);
        c.setFaction("ROMANS");
        return c;
    }

    private static void assertHas(List<String> problems, String fragment) {
        assertTrue(problems.stream().anyMatch(p -> p.contains(fragment)), "se esperaba un aviso con '" + fragment + "' y hay: " + problems);
    }

    @Test
    void theRealCatalogIsClean_andEveryCardBuilds() {
        var all = repository.findAll();
        assertEquals(36, all.size(), "data.sql debe cargar las 36 cartas");
        for (CardTemplateEntity e : all) {
            assertEquals(List.of(), CatalogChecker.check(e), e.getId());
            assertNotNull(mapper.toDomain(e), "la carta debe poder construirse como en una partida: " + e.getId());
        }
    }

    @Test
    void validRecipesProduceNoWarnings() {
        assertEquals(List.of(), CatalogChecker.check(card("UNIT", "NOT_A_SPELL", "1:ENEMY", "DISRUPTION", "ON_PLACED:ENEMY_DAMAGE:2")));
        assertEquals(List.of(), CatalogChecker.check(card("UNIT", "NOT_A_SPELL", "NONE", "DISRUPTION,JUMPER", "ON_DAMAGE_TAKEN:SELF_BUFF:1;ON_COMBAT_WIN:READY_SELF")));
        assertEquals(List.of(), CatalogChecker.check(card("UNIT", "NOT_A_SPELL", "1:ENEMY", "NONE", "ON_PLACED:HUNTER_MARK:1:5")));
        assertEquals(List.of(), CatalogChecker.check(card("SPELL", "SLOW", "NONE", "NONE", "ON_PLACED:DRAW:2")));
    }

    @Test
    void silentTypos_areReported() {
        assertHas(CatalogChecker.check(card("UNIT", "NOT_A_SPELL", "1:ENEMY", "NONE", "ON_PLACE:ENEMY_DAMAGE:2")), "no se disparara nunca");
        assertHas(CatalogChecker.check(card("UNIT", "NOT_A_SPELL", "1:ENEMY", "NONE", "ON_PLACED:ENEMY_DAMGE:2")), "no existe");
        assertHas(CatalogChecker.check(card("UNIT", "NOT_A_SPELL", "NONE", "NONE", "ON_PLACED:ENEMY_DAMAGE:2")), "necesita 'targets'");
        assertHas(CatalogChecker.check(card("UNIT", "NOT_A_SPELL", "1:ALLY", "NONE", "ON_PLACED:ENEMY_DAMAGE:2")), "solo daña unidades ENEMY");
        assertHas(CatalogChecker.check(card("UNIT", "NOT_A_SPELL", "NONE", "NONE", "ON_DAMAGE_TAKEN:ENEMY_DAMAGE:2")), "solo funciona con ON_PLACED");
        assertHas(CatalogChecker.check(card("SPELL", "FAST", "1:ENEMY", "NONE", "ON_COMBAT_WIN:ENEMY_DAMAGE:2")), "un hechizo solo ejecuta ON_PLACED");
        assertHas(CatalogChecker.check(card("UNIT", "NOT_A_SPELL", "NONE", "GLORY", "NONE")), "el motor no la lee");
    }

    @Test
    void thingsThatBreakTheMatch_areReportedAsErrors() {
        assertHas(CatalogChecker.check(card("UNIT", "NOT_A_SPELL", "NONE", "JUMPE", "NONE")), "ERROR");
        assertHas(CatalogChecker.check(card("UNIT", "NOT_A_SPELL", "NONE", "JUMPER;DISRUPTION", "NONE")), "separan con COMA");
        assertHas(CatalogChecker.check(card("UNIT", "NOT_A_SPELL", "NONE", "NONE", "ON_PLACED:SELF_BUFF:dos")), "no es un numero");
        assertHas(CatalogChecker.check(card("UNIT", "NOT_A_SPELL", "1:ENEMY", "NONE", "ON_PLACED:HUNTER_MARK:1")), "necesita 2 valor");
        assertHas(CatalogChecker.check(card("UNIT", "SLOW", "NONE", "NONE", "NONE")), "NOT_A_SPELL");
        assertHas(CatalogChecker.check(card("SPELL", "NOT_A_SPELL", "NONE", "NONE", "NONE")), "SLOW, FAST o INSTANT");
        assertHas(CatalogChecker.check(card("SPELL", "FAST", "1:GLOBAL", "NONE", "NONE")), "GLOBAL");
        CardTemplateEntity badFaction = card("UNIT", "NOT_A_SPELL", "NONE", "NONE", "NONE");
        badFaction.setFaction("ROMANOS");
        assertHas(CatalogChecker.check(badFaction), "faction 'ROMANOS' no existe");
    }
}
