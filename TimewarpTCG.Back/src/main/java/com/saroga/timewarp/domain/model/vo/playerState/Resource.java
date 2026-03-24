package com.saroga.timewarp.domain.model.vo.playerState;

import com.saroga.timewarp.domain.model.vo.card.Cost;
import com.saroga.timewarp.domain.model.vo.gameState.Turn;

public record Resource(int resource) {
    public Resource {
        if (resource < 0) resource = 0;
    }

    public static Resource of(int resource) {
        return new Resource(resource);
    }

    public Resource spend(Cost cost) {
        if (this.resource < cost.value()) {
            throw new IllegalStateException("No puedes jugar cartas que superen tu recurso actual");
        }
        return new Resource(this.resource - cost.value());
    }

    public Resource add(int amount) {
        return new Resource(this.resource + amount);
    }

    public static Resource newTurnResource(Turn turn) {
        int energy = Math.min(turn.value(), 10); // Máximo 10
        return new Resource(energy);
    }
}
