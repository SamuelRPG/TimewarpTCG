package com.saroga.timewarp.domain.model.enums;

public enum GamePhase {
    MAIN_OPEN,      // Tu turno, puedes hacer de todo (Unidades, SLOW, FAST...)
    MAIN_CLOSED,     // Alguien lanzó algo en MAIN, solo respuestas (FAST, INSTANT)
    COMBAT_OPEN,    // Fase de pelea, nadie ha lanzado hechizos aún
    COMBAT_CLOSED    // Alguien lanzó un truco de combate, solo respuestas
}
