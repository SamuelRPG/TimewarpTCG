package com.saroga.timewarp.domain.model.entity.card;

import com.saroga.timewarp.domain.model.enums.CardLocation;
import com.saroga.timewarp.domain.model.enums.CardType;
import com.saroga.timewarp.domain.model.vo.card.Cost;
import com.saroga.timewarp.domain.model.vo.card.Drive;

import java.util.UUID;

public class Card {
    private UUID id;
    private final UUID ownerId;
    private String name;
    private Drive drive;
    private Cost cost;
    private CardLocation cardLocation;
    private final CardType cardType;
    private boolean destroyed;

    public Card(UUID id, UUID ownerId, String name, Drive drive, Cost cost, CardType cardType) {
        this.id = id;
        this.ownerId = ownerId; // para saber a que jugador pertenece la carta
        this.name = name;
        this.drive = drive;
        this.cost = cost;
        this.cardLocation = CardLocation.DECK; // la carta empieza en el mazo
        this.cardType = cardType;
        this.destroyed = false;
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

        this.destroyed = true;
        this.onDestroyed();
    }

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
}
