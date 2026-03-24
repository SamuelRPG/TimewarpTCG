package com.saroga.timewarp.domain.model.vo.card;

public record Drive(int value) {
    public Drive {
        if (value < 0) value = 0;
    }

    public static Drive of (int value) {
        return new Drive(value);
    }
    public Drive increase(Drive other) {
        return new Drive(this.value + other.value());
    }

    public Drive decrease(Drive other) {
        return new Drive(this.value - other.value());
    }

    public boolean isZero() {
        return this.value == 0;
    }
}
