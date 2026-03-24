package com.saroga.timewarp.domain.model.vo.card;

public record Cost(int value) {

    public Cost {
        if (value < 0) value = 0;
    }

    public static Cost of(int cost) {
        return new Cost(cost);
    }

    public Cost reduce(Cost amount) {
        return new Cost(this.value - amount.value());
    }

    public Cost increase(Cost amount) {
        return new Cost(this.value + amount.value());
    }

    public boolean canAfford(int resource) {
        return this.value <= resource;
    }
}
