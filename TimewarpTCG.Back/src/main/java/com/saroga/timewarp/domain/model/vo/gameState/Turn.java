package com.saroga.timewarp.domain.model.vo.gameState;

public record Turn(int value) {
    public Turn {
        if (value < 0) value = 0;
    }

    public static Turn of(int turn) {
        return new Turn(turn);
    }

    public Turn increase() {
        return new Turn(this.value+1);
    }
}
