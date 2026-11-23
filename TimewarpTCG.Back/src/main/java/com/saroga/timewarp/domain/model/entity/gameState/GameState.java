package com.saroga.timewarp.domain.model.entity.gameState;

import com.saroga.timewarp.domain.model.effect.effectInterface.Effect;
import com.saroga.timewarp.domain.model.entity.playerState.PlayerState;
import com.saroga.timewarp.domain.model.entity.card.Card;
import com.saroga.timewarp.domain.model.enums.*;
import com.saroga.timewarp.domain.model.vo.gameState.QueuedSpell;
import com.saroga.timewarp.domain.model.vo.gameState.Turn;
import com.saroga.timewarp.domain.model.vo.playerState.Resource;
import com.saroga.timewarp.domain.model.vo.playerState.Score;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.UUID;

public class GameState {
    private final UUID gameId;
    private UUID activePlayerId;
    private final PlayerState playerA;
    private final PlayerState playerB;
    private Turn turnNumber;
    private Battlefield activeCombatZone;
    private GamePhase gamePhase;
    private List<Effect> pendingEffects;
    private final ArrayList<QueuedSpell> spellStack = new ArrayList<>();
    private ConsecutivePasses consecutivePasses;
    private GameStatus gameStatus;
    private UUID winnerId;
    private UUID roundStartPlayerId;

    public GameState(UUID id, PlayerState playerA, PlayerState playerB) {
        this.gameId = id;
        this.playerA = playerA;
        this.playerB = playerB;
        this.turnNumber = Turn.of(1);
        this.activePlayerId = playerA.hasPriority() ? playerA.getPlayerId() : playerB.getPlayerId();
        this.gamePhase = GamePhase.MAIN_OPEN;
        this.consecutivePasses = ConsecutivePasses.ZERO;
        this.gameStatus = GameStatus.ONGOING;
        winnerId = null;
        this.roundStartPlayerId = activePlayerId;
    }

    // SPELL QUEUE //

    public void pushSpell(QueuedSpell enqueuedSpell) {
        this.spellStack.add(enqueuedSpell);
    }

    public void resolveQueue() {
        if (spellStack.isEmpty()) { return; }

        for (int i = (spellStack.size() -1); i >= 0; i--) {
            QueuedSpell activeSpell = spellStack.get(i);
            Card spellCard = activeSpell.card();

            PlayerState owner = this.getPlayerById(spellCard.getOwnerId());
            PlayerState opponent = getOpponentOf(owner.getPlayerId());

            spellCard.onPlaced(this, owner, opponent, spellCard.getCardLocation(), activeSpell.targets());

            owner.getGraveyard().add(spellCard);
            spellCard.setCardLocation(CardLocation.GRAVEYARD);
        }

        spellStack.clear();
        cleanupDestroyed();
        openCurrentPhase();
    }

    /**
     * Retira del tablero las unidades marcadas como destruidas por un efecto (hechizo, ON_PLACED, etc.).
     * El combate ya las retira con PlayerState.destroyCard; los efectos solo las marcaban, y se quedaban
     * en el tablero (seguian puntuando y disputando carriles).
     */
    public void cleanupDestroyed() {
        for (PlayerState p : List.of(playerA, playerB)) {
            for (CardLocation loc : List.of(CardLocation.MY_BASE, CardLocation.FIELD_LEFT, CardLocation.FIELD_RIGHT)) {
                List<Card> zone = p.getUnitsIn(loc);
                List<Card> dead = new ArrayList<>();
                for (Card c : zone) { if (c.isDestroyed()) dead.add(c); }
                for (Card c : dead) {
                    zone.remove(c);
                    p.getGraveyard().add(c);
                    c.setCardLocation(CardLocation.GRAVEYARD);
                    p.getPendingMoves().remove(c);
                }
            }
        }
    }

    public boolean isStackEmpty() {
        return spellStack.isEmpty();
    }

    // pases consecutivos //

    public void addConsecutivePass() {
        switch (this.getConsecutivePasses()) {
            case ConsecutivePasses.ZERO -> consecutivePasses = ConsecutivePasses.ONE;
            case ConsecutivePasses.ONE -> consecutivePasses = ConsecutivePasses.TWO;
            case ConsecutivePasses.TWO -> consecutivePasses = ConsecutivePasses.TWO;
        }
    }

    public void resetConsecutivePasses() {
        this.consecutivePasses = ConsecutivePasses.ZERO;
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
        updateScores();
        checkWinCondition();

        if (this.gameStatus == GameStatus.FINISHED) {
            return;
        }

        this.turnNumber = turnNumber.increase();

        this.roundStartPlayerId =getOpponentOf(roundStartPlayerId).getPlayerId();

        PlayerState firstToAct = getPlayerById(roundStartPlayerId);
        PlayerState secondToAct = getOpponentOf(firstToAct.getPlayerId());

        firstToAct.setPriority(true);
        secondToAct.setPriority(false);
        this.activePlayerId = firstToAct.getPlayerId();

        playerA.setResource(Resource.newTurnResource(this.turnNumber));
        playerB.setResource(Resource.newTurnResource(this.turnNumber));

        playerA.readyAllCards();
        playerB.readyAllCards();

        playerA.drawCard();
        playerB.drawCard();

        playerA.getAllCardsOnBoard().forEach(c -> c.setCardLocationAtBeginningOfRound(c.getCardLocation()));
        playerB.getAllCardsOnBoard().forEach(c -> c.setCardLocationAtBeginningOfRound(c.getCardLocation()));

        this.gamePhase = GamePhase.MAIN_OPEN;
        this.consecutivePasses = ConsecutivePasses.ZERO;
        this.spellStack.clear();
    }

    private void updateScores() {
        List<CardLocation> possibleScoringLocations = List.of(CardLocation.FIELD_LEFT, CardLocation.FIELD_RIGHT);

        for (CardLocation lane : possibleScoringLocations) {
            if (playerA.hasUnitsIn(lane)) { playerA.scorePoint(); }
            if (playerB.hasUnitsIn(lane)) { playerB.scorePoint(); }
        }
    }

    private void checkWinCondition() {
        int scoreA = playerA.getScore().value(); // Asumiendo que tu Score tiene un .value()
        int scoreB = playerB.getScore().value();

        boolean aReachesLimit = scoreA >= 10;
        boolean bReachesLimit = scoreB >= 10;
        boolean hasTwoPointMargin = Math.abs(scoreA - scoreB) >= 2;

        // Si alguien llegó a 10 Y hay una ventaja de 2 puntos
        if ((aReachesLimit || bReachesLimit) && hasTwoPointMargin) {
            this.gameStatus = GameStatus.FINISHED;

            if (scoreA > scoreB) {
                this.winnerId = playerA.getPlayerId();
            } else {
                this.winnerId = playerB.getPlayerId();
            }
        }
    }

    // GETTERS POR ID //

    public PlayerState getPlayerById(UUID id) {
        if (playerA.getPlayerId().equals(id)) {
            return playerA;
        }
        if (playerB.getPlayerId().equals(id)) {
            return playerB;
        }
        throw new IllegalArgumentException("El ID " + id + " no pertenece a ningún jugador de esta partida.");
    }

    public Card getCardById(UUID cardId) {
        List<Card> allCardsInGame = new ArrayList<>();

        allCardsInGame.addAll(playerA.getAllCardsOnBoard());
        allCardsInGame.addAll(playerA.getHand());
        allCardsInGame.addAll(playerA.getGraveyard());

        allCardsInGame.addAll(playerB.getAllCardsOnBoard());
        allCardsInGame.addAll(playerB.getHand());
        allCardsInGame.addAll(playerB.getGraveyard());

        return allCardsInGame.stream()
                .filter(c -> c.getId().equals(cardId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No se encontro la carta"));
    }

    public PlayerState getOpponentOf(UUID id) {
        return playerA.getPlayerId().equals(id) ? playerB : playerA;
    }

    // En GameState.java
    public void openCurrentPhase() {
        if (this.gamePhase == GamePhase.MAIN_CLOSED) {
            this.gamePhase = GamePhase.MAIN_OPEN;
        } else if (this.gamePhase == GamePhase.COMBAT_CLOSED) {
            this.gamePhase = GamePhase.COMBAT_OPEN;
        }
        // Si ya estaba OPEN, no hace nada.
    }

    public void closeCurrentPhase() {
        if (this.gamePhase == GamePhase.MAIN_OPEN) {
            this.gamePhase = GamePhase.MAIN_CLOSED;
        } else if (this.gamePhase == GamePhase.COMBAT_OPEN) {
            this.gamePhase = GamePhase.COMBAT_CLOSED;
        }
    }

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

    public GamePhase getGamePhase() {
        return gamePhase;
    }

    public void setGamePhase(GamePhase gamePhase) {
        this.gamePhase = gamePhase;
    }

    public List<Effect> getPendingEffects() {
        return pendingEffects;
    }

    public void setPendingEffects(List<Effect> pendingEffects) {
        this.pendingEffects = pendingEffects;
    }

    public List<QueuedSpell> getSpellStack() {
        return spellStack;
    }

    public ConsecutivePasses getConsecutivePasses() {
        return consecutivePasses;
    }

    public void setConsecutivePasses(ConsecutivePasses consecutivePasses) {
        this.consecutivePasses = consecutivePasses;
    }

    public GameStatus getGameStatus() { return gameStatus;}
    public UUID getWinnerId() { return winnerId; }

    /** Un jugador abandona, se desconecta o se queda sin tiempo: la partida termina y gana el rival. No hace nada si ya acabo. */
    public void concede(UUID loserId) {
        if (this.gameStatus == GameStatus.FINISHED) return;
        this.winnerId = getOpponentOf(loserId).getPlayerId();
        this.gameStatus = GameStatus.FINISHED;
    }
}
