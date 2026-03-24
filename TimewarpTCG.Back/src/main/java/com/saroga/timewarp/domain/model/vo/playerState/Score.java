package com.saroga.timewarp.domain.model.vo.playerState;

public record Score(int value) {
    public Score {
        if (value < 0) value = 0;
    }

    public static Score of(int value) {
        return new Score(value);
    }

    public Score scorePoint() {
        return new Score(this.value+1);
    }
}
