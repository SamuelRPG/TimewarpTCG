package com.saroga.timewarp.infrastructure.web.dto.response;

/** Avisos del emparejamiento por /topic/player/{id}/status. type: QUEUED, MATCHED, LEFT o TIMEOUT. */
public class MatchStatusDto {
    public String type;
    public String opponentName;     // solo en MATCHED
    public String message;

    public MatchStatusDto(String type, String opponentName, String message) {
        this.type = type;
        this.opponentName = opponentName;
        this.message = message;
    }
}
