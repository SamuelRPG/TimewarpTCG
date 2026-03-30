package com.saroga.timewarp.domain.model.entity.card;

import com.saroga.timewarp.domain.model.effect.effectInterface.Effect;
import com.saroga.timewarp.domain.model.entity.playerState.PlayerState;
import com.saroga.timewarp.domain.model.enums.CardLocation;
import com.saroga.timewarp.domain.model.enums.CardType;
import com.saroga.timewarp.domain.model.enums.Keyword;
import com.saroga.timewarp.domain.model.vo.card.Cost;
import com.saroga.timewarp.domain.model.vo.card.Drive;

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

    private List<Keyword> keywords;
    private List<Effect> effects;

    public Card(UUID id, UUID ownerId, String name, Drive initialDrive, Drive drive, Cost cost, CardType cardType, List<Keyword> keywords, List<Effect> effects) {
        this(id, ownerId, name, initialDrive, drive, cost, cardType); // Llama al de abajo
        this.keywords = keywords != null ? new ArrayList<>(keywords) : new ArrayList<>();
        this.effects = effects != null ? new ArrayList<>(effects) : new ArrayList<>();
    }

    public Card(UUID id, UUID ownerId, String name, Drive initialDrive, Drive drive, Cost cost, CardType cardType) {
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
        this.keywords = new ArrayList<>();
        this.effects = new ArrayList<>();
        this.cardLocationAtBeginningOfRound = CardLocation.DECK;
    }

    public void takeDamage(Drive amount) {
        if (this.destroyed) return;

        this.drive = drive.decrease(amount);

        // Notificar a los efectos que hemos recibido daño (por si alguno salta)
        effects.forEach(e -> e.onDamageTaken(this, amount));

        if (this.drive.isZero()) {
            this.destroy();
        }
    }

    public void onPlaced(PlayerState player, CardLocation location) {
        effects.forEach(e -> e.onPlaced(this, player, location));
    }

    public void onCombatWin(PlayerState player, CardLocation lane) {
        effects.forEach(e -> e.onCombatWin(this, player, lane));
    }

    public Drive getEffectiveDrive() {
        int bonus = effects.stream().mapToInt(e -> e.modifyDrive(this)).sum();
        return Drive.of(this.drive.value() + bonus);
    }

    public void buffDrive(Drive amount) {
        if (this.destroyed) return;

        this.drive = drive.increase(amount);
    }

    public void destroy() {
        if (this.destroyed) return;

        this.drive = Drive.of(0);
        this.destroyed = true;
        this.onDestroyed();
    }

    public void onMovementConfirmed(PlayerState player, PlayerState opponent, CardLocation lane) {
        effects.forEach(e -> e.onMovementConfirmed(this, player, opponent, lane));
    }

    public void beforeCombat() {
        effects.forEach(e -> e.beforeCombat(this));
    }

    public void exhaust() { this.exhausted = true; }

    public void ready() {this.exhausted = false; }

    public void onDestroyed() {
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
}
