package com.saroga.timewarp.domain.model.entity.gameState;

import com.saroga.timewarp.domain.model.entity.playerState.PlayerState;
import com.saroga.timewarp.domain.model.entity.card.Card;
import com.saroga.timewarp.domain.model.enums.Battlefield;
import com.saroga.timewarp.domain.model.enums.CardLocation;
import com.saroga.timewarp.domain.model.vo.gameState.Turn;
import com.saroga.timewarp.domain.model.vo.playerState.Resource;
import com.saroga.timewarp.domain.model.vo.playerState.Score;

import java.util.List;
import java.util.UUID;

public class GameState {
    private final UUID gameId;
    private UUID activePlayerId;
    private final PlayerState playerA;
    private final PlayerState playerB;
    private Turn turnNumber;
    private Battlefield activeCombatZone;

    public GameState(UUID id, PlayerState playerA, PlayerState playerB) {
        this.gameId = id;
        this.playerA = playerA;
        this.playerB = playerB;
        this.turnNumber = Turn.of(0);
        this.activePlayerId = playerA.getPlayerId();
    }

    // logica de turnos y prioridades //

    public void switchPriority() {
            playerA.setPriority(!playerA.hasPriority());
            playerB.setPriority(!playerB.hasPriority());

            this.activePlayerId = playerA.hasPriority() ? playerA.getPlayerId() : playerB.getPlayerId();
    }

    public boolean isBattlefieldContested(CardLocation battlefield) {
        if (playerA.hasUnitsIn(battlefield) && playerB.hasUnitsIn(battlefield)) {
            return true;
        }
        return false;
    }

    public void newTurn() {
        this.turnNumber = turnNumber.increase();

        playerA.setResource(Resource.newTurnResource(this.turnNumber));
        playerB.setResource(Resource.newTurnResource(this.turnNumber));

        playerA.readyAllCards();
        playerB.readyAllCards();

        playerA.drawCard();
        playerB.drawCard();

        playerA.getAllCardsOnBoard().forEach(c -> c.setCardLocationAtBeginningOfRound(c.getCardLocation()));
        playerB.getAllCardsOnBoard().forEach(c -> c.setCardLocationAtBeginningOfRound(c.getCardLocation()));

        updateScores();
    }

    private void updateScores() {
        List<CardLocation> possibleScoringLocations = List.of(CardLocation.FIELD_LEFT, CardLocation.FIELD_RIGHT);

        for (CardLocation lane : possibleScoringLocations) {
            if (playerA.hasUnitsIn(lane)) { playerA.scorePoint(); }
            if (playerB.hasUnitsIn(lane)) { playerB.scorePoint(); }
        }
    }

    public PlayerState getPlayerById(UUID id) {
        if (playerA.getPlayerId().equals(id)) {
            return playerA;
        }
        if (playerB.getPlayerId().equals(id)) {
            return playerB;
        }
        throw new IllegalArgumentException("El ID " + id + " no pertenece a ningún jugador de esta partida.");
    }

    public PlayerState getOpponentOf(UUID id) {
        return playerA.getPlayerId().equals(id) ? playerB : playerA;
    }




    // efectos de cartas //


    // getter y setter //

    public Turn getTurnNumber() {
        return turnNumber;
    }

    public void setTurnNumber(Turn turnNumber) {
        this.turnNumber = turnNumber;
    }

    public PlayerState getPlayerB() {
        return playerB;
    }

    public PlayerState getPlayerA() {
        return playerA;
    }

    public UUID getActivePlayerId() {
        return activePlayerId;
    }

    public void setActivePlayerId(UUID activePlayerId) {
        this.activePlayerId = activePlayerId;
    }

    public UUID getGameId() {
        return gameId;
    }
}
