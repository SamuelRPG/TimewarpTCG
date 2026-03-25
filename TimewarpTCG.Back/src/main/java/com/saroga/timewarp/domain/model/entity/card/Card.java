package com.saroga.timewarp.domain.model.entity.card;

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
    private List<Keyword> keywords;

    public Card(UUID id, UUID ownerId, String name, Drive initialDrive, Drive drive, Cost cost, CardType cardType, List<Keyword> keywords) {
        this(id, ownerId, name, initialDrive, drive, cost, cardType); // Llama al de abajo
        this.keywords = new ArrayList<>(keywords);
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
    }

    public void takeDamage(Drive damage) {
        if (this.destroyed) return;

        this.drive = drive.decrease(damage);

        if (this.drive.isZero()) {
            this.destroyed = true;
        }
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
}
