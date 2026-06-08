package com.saroga.timewarp.domain.model.entity.card;

import com.saroga.timewarp.domain.model.effect.effectInterface.Effect;
import com.saroga.timewarp.domain.model.entity.cardTemplate.CardTemplate;
import com.saroga.timewarp.domain.model.entity.gameState.GameState;
import com.saroga.timewarp.domain.model.entity.playerState.PlayerState;
import com.saroga.timewarp.domain.model.enums.*;
import com.saroga.timewarp.domain.model.vo.card.Cost;
import com.saroga.timewarp.domain.model.vo.card.Drive;
import com.saroga.timewarp.domain.model.vo.card.TargetingRequirement;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Card {
    private UUID id;
    private final UUID ownerId;
    private String name;
    private Drive initialDrive;
    private Drive drive;
    private Cost cost;
    private final CardType cardType;
    private CardLocation cardLocation;
    private boolean exhausted;
    private boolean destroyed;
    private CardLocation cardLocationAtBeginningOfRound;
    private List<TargetingRequirement> targetingRequirements;
    private SpellSpeed spellSpeed;

    private List<Keyword> keywords;
    private List<Effect> effects;
    private Faction faction;

    public Card(UUID id, UUID ownerId, String name, Drive initialDrive, Drive drive, Cost cost,
                CardType cardType, List<Keyword> keywords, List<Effect> effects, List<TargetingRequirement> targetingRequirements, SpellSpeed spellSpeed, Faction faction) {
        this.id = id;
        this.ownerId = ownerId; // para saber a que jugador pertenece la carta
        this.name = name;
        this.initialDrive = initialDrive;
        this.drive = drive;
        this.cost = cost;
        this.cardType = cardType;
        this.cardLocation = CardLocation.DECK; // la carta empieza en el mazo
        this.exhausted = true;
        this.destroyed = false;
        this.cardLocationAtBeginningOfRound = CardLocation.DECK;
        this.keywords = keywords != null ? new ArrayList<>(keywords) : new ArrayList<>();
        this.effects = effects != null ? new ArrayList<>(effects) : new ArrayList<>();
        this.targetingRequirements = targetingRequirements != null ? new ArrayList<>(targetingRequirements) : new ArrayList<>();
        this.spellSpeed = spellSpeed;
        this.faction = faction;
    }

    public static Card createUnit(UUID ownerId, String name, Drive stats, Cost cost,
                                  List<Keyword> keywords, List<Effect> effects,
                                  List<TargetingRequirement> targetingRequirements, Faction faction) {
        return new Card(UUID.randomUUID(), ownerId, name, stats, stats, cost,
                CardType.UNIT, keywords, effects, targetingRequirements, SpellSpeed.NOT_A_SPELL, faction );
    }

    public static Card createSpell(UUID ownerId, String name, Cost cost,
                                   List<Effect> effects,
                                   List<TargetingRequirement> targetingRequirements, SpellSpeed spellSpeed, Faction faction) {
        return new Card(UUID.randomUUID(), ownerId, name, Drive.of(0), Drive.of(0), cost,
                CardType.SPELL, new ArrayList<>(), effects, targetingRequirements, spellSpeed, faction);
    }

    // Este es el "Clonador" que coge los datos de la BBDD (Plantilla) y crea la carta viva
    public static Card fromTemplate(CardTemplate template, UUID ownerId) {
        if (template.getCardType() == CardType.UNIT) {
            return createUnit(
                    ownerId,
                    template.getName(),
                    template.getBaseDrive(), // Ya es de tipo Drive
                    template.getBaseCost(),  // Ya es de tipo Cost
                    template.getKeywords(),
                    template.getEffects(),
                    template.getTargetingRequirements(),
                    template.getFaction()
            );
        } else if (template.getCardType() == CardType.SPELL) {
            return createSpell(
                    ownerId,
                    template.getName(),
                    template.getBaseCost(),
                    template.getEffects(),
                    template.getTargetingRequirements(),
                    template.getSpellSpeed(),
                    template.getFaction()
            );
        }
        throw new IllegalArgumentException("Tipo de carta no soportado: " + template.getCardType());
    }

    public boolean isUnit() {
        return this.cardType == CardType.UNIT;
    }

    public boolean isSpell() {
        return this.cardType == CardType.SPELL;
    }

    public void takeDamage(GameState game, Drive amount) {
        if (this.destroyed) return;

        this.drive = drive.decrease(amount);

        // Notificar a los efectos que hemos recibido daño (por si alguno salta)
        effects.forEach(e -> e.onDamageTaken(game, this, amount));

        if (this.drive.isZero()) {
            this.destroy(game);
        }
    }

    public void onPlaced(GameState game, PlayerState player, PlayerState opponent, CardLocation location, List<Card> targets) {
        effects.forEach(e -> e.onPlaced(game, this, player, opponent, location, targets));
    }

    public void onCombatWin(GameState game, PlayerState player, CardLocation lane) {
        effects.forEach(e -> e.onCombatWin(game,this, player, lane));
    }

    public Drive getEffectiveDrive(GameState game) {
        int bonus = effects.stream().mapToInt(e -> e.modifyDrive(game,this)).sum();
        return Drive.of(this.drive.value() + bonus);
    }

    public void buffDrive(Drive amount) {
        if (this.destroyed) return;

        this.drive = drive.increase(amount);
    }

    public void destroy(GameState game) {
        if (this.destroyed) return;

        this.drive = Drive.of(0);
        this.destroyed = true;
        this.onDestroyed(game);
    }

    public void onMovementConfirmed(GameState game,PlayerState player, PlayerState opponent, CardLocation lane) {
        effects.forEach(e -> e.onMovementConfirmed(game, this, player, opponent, lane));
    }

    public void beforeCombat(GameState game) {
        effects.forEach(e -> e.beforeCombat(game, this));
    }

    public void exhaust() { this.exhausted = true; }

    public void ready() {this.exhausted = false; }

    public void onDestroyed(GameState game) {
        effects.forEach(e -> e.onDestroyed(game, this));
        System.out.println("Efectos que hace la carta al ser destruida");
    }

    public void makeCheaper(Cost amount) {
        this.cost = cost.reduce(amount);
    }

    public void makeMoreExpensive(Cost amount) {
        this.cost = cost.increase(amount);
    }

    public boolean isAlive() {
        return !destroyed;
    }

    public boolean hasKeyword(Keyword k) { return this.keywords.contains(k); }

    // getter y setter //
    public Drive getDrive() {
        return drive;
    }
    public boolean isExhausted() { return exhausted; }
    public UUID getId() {return id;}
    public UUID getOwnerId() {return ownerId;}
    public String getName() {return name;}
    public Cost getCost() {return cost;}
    public CardType getCardType() {return cardType;}
    public CardLocation getCardLocation() {return cardLocation;}
    public void setCardLocation(CardLocation location) {this.cardLocation = location;}
    public boolean isDestroyed() {return destroyed;}
    public List<Keyword> getKeywords() {return keywords;}
    public Drive getInitialDrive() {return initialDrive;}
    public CardLocation getCardLocationAtBeginningOfRound() { return cardLocationAtBeginningOfRound;}
    public void setCardLocationAtBeginningOfRound(CardLocation cardLocationAtBeginningOfRound) { this.cardLocationAtBeginningOfRound = cardLocationAtBeginningOfRound;}
    public List<TargetingRequirement> getTargetingRequirements() {return targetingRequirements;}
    public void setTargetingRequirements(List<TargetingRequirement> targetingRequirements) {this.targetingRequirements = targetingRequirements;}
    public SpellSpeed getSpellSpeed() {return spellSpeed;}
    public void setSpellSpeed(SpellSpeed spellSpeed) {this.spellSpeed = spellSpeed;}
    public List<Effect> getEffects() {return effects;}
    public Faction getFaction() {return faction;}
}
