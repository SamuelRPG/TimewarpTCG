package com.saroga.timewarp.domain.model.entity.playerState;

import com.saroga.timewarp.domain.model.entity.card.Card;
import com.saroga.timewarp.domain.model.entity.gameState.GameState;
import com.saroga.timewarp.domain.model.enums.CardLocation;
import com.saroga.timewarp.domain.model.enums.ConsecutivePasses;
import com.saroga.timewarp.domain.model.enums.GamePhase;
import com.saroga.timewarp.domain.model.enums.Keyword;
import com.saroga.timewarp.domain.model.vo.playerState.Resource;
import com.saroga.timewarp.domain.model.vo.playerState.Score;
import com.saroga.timewarp.domain.service.CombatService;

import java.util.*;

public class PlayerState {
    private final UUID playerId;
    private boolean priority;
    private Resource resource;
    private Score score;
    private Set<Card> pendingMoves = new HashSet<>();

    private List<Card> deck;
    private List<Card> hand;
    private List<Card> graveyard;
    private List<Card> base;
    private List<Card> fieldLeft;
    private List<Card> fieldRight;

    public PlayerState(UUID playerId, List<Card> deck, boolean hasPriority) {
        this.playerId = playerId;
        this.priority = hasPriority;
        this.resource = Resource.of(1);
        this.score = Score.of(0);

        this.deck = deck;
        this.hand = new ArrayList<>();
        this.graveyard = new ArrayList<>();
        this.base = new ArrayList<>();
        this.fieldLeft = new ArrayList<>();
        this.fieldRight = new ArrayList<>();
    }

    public List<Card> getAllCardsOnBoard() {
        List<Card> cardsInPlay = new ArrayList<>();
        cardsInPlay.addAll(base);
        cardsInPlay.addAll(fieldLeft);
        cardsInPlay.addAll(fieldRight);

        return cardsInPlay;
    }

    public void scorePoint() {
        this.score = this.score.scorePoint();
    }

    public void drawCard() {
        if (deck.isEmpty()) {
            return;
        }

        hand.add(deck.getFirst());
        deck.removeFirst();
    }

    public void readyAllCards() {
        base.forEach(Card::ready);
        fieldRight.forEach(Card::ready);
        fieldLeft.forEach(Card::ready);
    }

    public void destroyCard(Card card) {
        card.destroy();
        this.graveyard.add(card);

        switch (card.getCardLocation()) {
            case FIELD_LEFT -> fieldLeft.remove(card);
            case FIELD_RIGHT -> fieldRight.remove(card);
            case MY_BASE -> base.remove(card);
        }
    }

    public boolean hasUnitsIn(CardLocation location) {
        List<Card> cards = getUnitsIn(location);
        return !cards.isEmpty();
    }

    public List<Card> getUnitsIn(CardLocation location) {
        return switch (location) {
            case FIELD_LEFT -> fieldLeft;
            case FIELD_RIGHT -> fieldRight;
            case MY_BASE -> base;
            case GRAVEYARD -> graveyard;
            case DECK -> deck;
            case HAND -> hand;
        };
    }

    public void executePlayCard(Card card, CardLocation location) {
        this.resource.spend(card.getCost());

        this.getHand().remove(card);

        if (card.isSpell()) {
        } else if (card.isUnit()) {
            removeCardFromCurrentLocation(card);
            addCardToLocation(card, location);
        }
    }

    public void moveCard(Card card, CardLocation destination) {
        validateMovement(card, destination);
        removeCardFromCurrentLocation(card);
        addCardToLocation(card, destination);

        card.setCardLocation(destination);
        pendingMoves.add(card);
    }

    public void confirmMoveAndPassPriority(GameState gameState, CombatService combatService) {
        if (!this.priority) {
            throw new IllegalStateException("No tienes la prioridad.");
        }

        boolean hasMovedUnits = !this.pendingMoves.isEmpty();
        boolean hasSpells = !gameState.isStackEmpty();

        // --- 1. PROCESAR ACCIONES FÍSICAS ---
        if (hasMovedUnits) {
            resolvePendingMoves(gameState);
            updatePhaseAfterMovement(gameState, combatService);
            this.pendingMoves.clear();
            gameState.resetConsecutivePasses(); // Al actuar, el rival vuelve a tener ventana

            gameState.switchPriority();
        }

        // --- 2. GESTIONAR PASES CONSECUTIVOS ---
        // Entramos aquí si el jugador NO ha movido unidades (ha dado a "Pasar/Aceptar")
        else {
            gameState.addConsecutivePass();
            // --- 3. ¿QUÉ OCURRE TRAS EL PASE? ---
            if (gameState.getConsecutivePasses() == ConsecutivePasses.TWO) {

                if (hasSpells) {
                    // Caso A: Había hechizos -> Se resuelve la pila
                    gameState.resolveQueue();
                    gameState.resetConsecutivePasses();

                    gameState.switchPriority();
                    // IMPORTANTE: Tras resolver hechizos, NO suele haber combate automático,
                    // se abre otra ventana de reacción.
                } else {
                    // Caso B: No había hechizos y es el segundo pase -> Resolución de Fase
                    handlePhaseEnd(gameState, combatService);
                    gameState.resetConsecutivePasses();
                }
            } else {
                gameState.switchPriority();
            }
        }

    }

    private void handlePhaseEnd(GameState gameState, CombatService combatService) {
        GamePhase currentPhase = gameState.getGamePhase();

        // Si ambos pasan en fase de combate, se ejecutan las tortas
        if (currentPhase == GamePhase.COMBAT_OPEN || currentPhase == GamePhase.COMBAT_CLOSED) {
            checkAndResolveCombat(gameState, combatService);
            // Tras el combate, podrías volver a MAIN o terminar ronda
        }
        // Si ambos pasan en fase principal, se termina la ronda o se cambia de turno
        else if (currentPhase == GamePhase.MAIN_OPEN || currentPhase == GamePhase.MAIN_CLOSED) {
            gameState.newTurn();
        }
    }

    private void checkAndResolveCombat(GameState gameState, CombatService combatService) {
        // Usamos tu CombatService para ver si hay que pegarse
        if (combatService.hasFieldUnitsFromBothPlayers(gameState.getPlayerA(), gameState.getPlayerB(), CardLocation.FIELD_LEFT)) {
            combatService.resolveLaneCombat(gameState, CardLocation.FIELD_LEFT);
        }

        if (combatService.hasFieldUnitsFromBothPlayers(gameState.getPlayerA(), gameState.getPlayerB(), CardLocation.FIELD_RIGHT)) {
            combatService.resolveLaneCombat(gameState, CardLocation.FIELD_RIGHT);
        }

        gameState.setGamePhase(GamePhase.MAIN_OPEN);
    }

    private void resolvePendingMoves(GameState gameState) {
        PlayerState opponent = gameState.getOpponentOf(this.getPlayerId());

        for (Card card : pendingMoves) {
            if (hasCardMovedThisRound(card)) {
                card.onMovementConfirmed(this, opponent, card.getCardLocation());
                card.setCardLocationAtBeginningOfRound(card.getCardLocation());
                card.exhaust();
            }
        }
    }

    private void updatePhaseAfterMovement(GameState gameState, CombatService combatService) {
        // Si ya estamos en combate, no hace falta chequear nada
        if (gameState.getGamePhase() == GamePhase.COMBAT_OPEN ||
                gameState.getGamePhase() == GamePhase.COMBAT_CLOSED) return;

        // Miramos si alguno de los dos carriles tiene ahora unidades de ambos bandos
        boolean isContested =
                combatService.hasFieldUnitsFromBothPlayers(gameState.getPlayerA(), gameState.getPlayerB(), CardLocation.FIELD_LEFT) ||
                        combatService.hasFieldUnitsFromBothPlayers(gameState.getPlayerA(), gameState.getPlayerB(), CardLocation.FIELD_RIGHT);

        if (isContested) {
            // Promocionamos la fase a Combate Abierto
            gameState.setGamePhase(GamePhase.COMBAT_OPEN);
        }
    }

    private void removeCardFromCurrentLocation(Card card) {
        switch(card.getCardLocation()) {
            case FIELD_LEFT -> fieldLeft.remove(card);
            case FIELD_RIGHT -> fieldRight.remove(card);
            case MY_BASE -> base.remove(card);
        }
    }

    public void addCardToLocation(Card card, CardLocation destination) {
        switch (destination) {
            case FIELD_LEFT -> fieldLeft.add(card);
            case FIELD_RIGHT -> fieldRight.add(card);
            case MY_BASE -> base.add(card);
        }
    }

    private void validateMovement(Card card, CardLocation destination) {
        if (card.getCardLocation() == destination) {
            throw new IllegalStateException("Mueve la carta a un battlefield diferente");
        }
        if (card.getCardLocation() == CardLocation.GRAVEYARD || card.getCardLocation() == CardLocation.DECK ||card.getCardLocation() == CardLocation.HAND) {
            throw new IllegalStateException("No se pueden mover cartas que no están en juego");
        }
        if (destination == CardLocation.GRAVEYARD || destination == CardLocation.DECK || destination == CardLocation.HAND) {
            throw new IllegalStateException("No se pueden mover cartas que no están en juego");
        }
        if (card.isExhausted()) {
            throw new IllegalStateException("No puedes mover una carta exhausta");
        }
        if (isField(card.getCardLocation()) && isField(destination)) {
            if (!card.hasKeyword(Keyword.JUMPER)) {
                throw new IllegalStateException("No puedes mover unidades de un field a otro");
            }
        }
    }

    private boolean hasCardMovedThisRound(Card card) {
        return card.getCardLocation() != card.getCardLocationAtBeginningOfRound();
    }

    private boolean isField(CardLocation location) {
        return location == CardLocation.FIELD_LEFT || location == CardLocation.FIELD_RIGHT;
    }

    // getter y setter //

    public Score getPoints() {
        return score;
    }

    public void setPoints(Score points) {
        this.score = points;
    }

    public Resource getResource() {
        return resource;
    }

    public void setResource(Resource resource) {
        this.resource = resource;
    }

    public boolean hasPriority() {
        return priority;
    }

    public void setPriority(boolean priority) {
        this.priority = priority;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public boolean isPriority() {
        return priority;
    }

    public Score getScore() {
        return score;
    }

    public List<Card> getDeck() {
        return deck;
    }

    public List<Card> getHand() {
        return hand;
    }

    public List<Card> getGraveyard() {
        return graveyard;
    }

    public List<Card> getBase() {
        return base;
    }

    public List<Card> getFieldLeft() {
        return fieldLeft;
    }

    public List<Card> getFieldRight() {
        return fieldRight;
    }
}
