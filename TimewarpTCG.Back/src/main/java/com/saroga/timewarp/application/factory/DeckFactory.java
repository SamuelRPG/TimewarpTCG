package com.saroga.timewarp.application.factory;

import com.saroga.timewarp.domain.model.entity.cardTemplate.CardTemplate;
import com.saroga.timewarp.domain.model.entity.deck.Deck;
import com.saroga.timewarp.domain.model.enums.CardType;
import com.saroga.timewarp.domain.model.enums.Faction;
import com.saroga.timewarp.domain.model.enums.SpellSpeed;
import com.saroga.timewarp.domain.model.vo.card.Cost;
import com.saroga.timewarp.domain.model.vo.card.Drive;

import java.util.ArrayList;
import java.util.List;

public class DeckFactory {

    // MAZO 1: Mazo Agresivo (Unidades rápidas y baratas)
    public Deck createAggroDeck() {
        List<CardTemplate> templates = new ArrayList<>();

        // Metemos 25 Guerreros Básicos
        for (int i = 0; i < 25; i++) {
            templates.add(new CardTemplate(
                    "U_AE_01",                              // id
                    "Infantería Rápida",                    // name
                    "Unidad básica y veloz del imperio.",   // description
                    Drive.of(2),                            // baseDrive (VO)
                    Cost.of(1),                             // baseCost (VO)
                    CardType.UNIT,                          // cardType
                    SpellSpeed.NOT_A_SPELL,                 // spellSpeed
                    new ArrayList<>(),                      // targetingRequirements
                    new ArrayList<>(),                      // keywords
                    new ArrayList<>(),                      // effects
                    Faction.ANCIENT_EGYPT                   // faction
            ));
        }

        // Metemos 15 Tanques
        for (int i = 0; i < 15; i++) {
            templates.add(new CardTemplate(
                    "U_AE_02",                              // id
                    "Vanguardia Pesada",                    // name
                    "Tanque resistente.",                   // description
                    Drive.of(5),                            // baseDrive (VO)
                    Cost.of(3),                             // baseCost (VO)
                    CardType.UNIT,                          // cardType
                    SpellSpeed.NOT_A_SPELL,                 // spellSpeed
                    new ArrayList<>(),                      // targetingRequirements
                    new ArrayList<>(),                      // keywords
                    new ArrayList<>(),                      // effects
                    Faction.ANCIENT_EGYPT                   // faction
            ));
        }

        return new Deck(templates);
    }

    // MAZO 2: Mazo de Control (Hechizos y unidades defensivas)
    public Deck createControlDeck() {
        List<CardTemplate> templates = new ArrayList<>();

        // 20 Muros Defensivos
        for (int i = 0; i < 20; i++) {
            templates.add(new CardTemplate(
                    "U_AE_03",                              // id
                    "Muro de Energía",                      // name
                    "Defensa estática.",                    // description
                    Drive.of(4),                            // baseDrive (VO)
                    Cost.of(1),                             // baseCost (VO)
                    CardType.UNIT,                          // cardType
                    SpellSpeed.NOT_A_SPELL,                 // spellSpeed
                    new ArrayList<>(),                      // targetingRequirements
                    new ArrayList<>(),                      // keywords
                    new ArrayList<>(),                      // effects
                    Faction.ANCIENT_EGYPT                   // faction
            ));
        }

        // 20 Hechizos básicos
        for (int i = 0; i < 20; i++) {
            templates.add(new CardTemplate(
                    "S_AE_01",                              // id
                    "Chispa Temporal",                      // name
                    "Hechizo de control.",                  // description
                    Drive.of(0),                            // baseDrive (VO - Hechizos tienen 0 Drive usualmente)
                    Cost.of(1),                             // baseCost (VO)
                    CardType.SPELL,                         // cardType
                    SpellSpeed.FAST,                        // spellSpeed
                    new ArrayList<>(),                      // targetingRequirements
                    new ArrayList<>(),                      // keywords
                    new ArrayList<>(),                      // effects
                    Faction.ANCIENT_EGYPT                   // faction
            ));
        }

        return new Deck(templates);
    }

    // Método auxiliar para que el Caso de Uso elija por nombre (ya sin UUID)
    public Deck getDeckByName(String deckName) {
        if ("AGGRO".equalsIgnoreCase(deckName)) {
            return createAggroDeck();
        } else if ("CONTROL".equalsIgnoreCase(deckName)) {
            return createControlDeck();
        } else {
            // Mazo por defecto si falla el nombre
            return createAggroDeck();
        }
    }
}