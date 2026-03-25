package com.saroga.timewarp.domain.model.enums;

public enum Battlefield {
    LEFT,
    RIGHT;

    public static Battlefield fromLocation(CardLocation location) {
        return switch (location) {
            case FIELD_LEFT -> LEFT;
            case FIELD_RIGHT -> RIGHT;
            default -> throw new IllegalArgumentException("Esta ubicación no es un campo de batalla");
        };
    }

    // Y viceversa (útil para buscar en las listas del PlayerState)
    public CardLocation toLocation() {
        return this == LEFT ? CardLocation.FIELD_LEFT : CardLocation.FIELD_RIGHT;
    }
}
