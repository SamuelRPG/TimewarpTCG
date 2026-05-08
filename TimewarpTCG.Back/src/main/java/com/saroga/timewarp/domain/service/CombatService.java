package com.saroga.timewarp.domain.service;

import com.saroga.timewarp.domain.model.entity.card.Card;
import com.saroga.timewarp.domain.model.entity.gameState.GameState;
import com.saroga.timewarp.domain.model.entity.playerState.PlayerState;
import com.saroga.timewarp.domain.model.enums.CardLocation;
import com.saroga.timewarp.domain.model.vo.card.Drive;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class CombatService {

    //se llamara a este metodo cuando ambos jugadores pasen prioridad //
    public void resolveLaneCombat(GameState gameState, CardLocation lane) {
        PlayerState pA = gameState.getPlayerA();
        PlayerState pB = gameState.getPlayerB();

        if (!hasFieldUnitsFromBothPlayers(pA, pB, lane)) {
            throw new IllegalStateException("No hay unidades de ambos jugadores para provocar un combate");
        }

        Drive totalDrivePA = calculateTotalDrive(gameState, pA, lane);
        Drive totalDrivePB = calculateTotalDrive(gameState, pB, lane);

        applyDamage(gameState, totalDrivePA, pB, lane);
        applyDamage(gameState, totalDrivePB, pA, lane);

        if (totalDrivePA.value() == totalDrivePB.value()) {
            System.out.println("Empate");
        } else {
            PlayerState winner = determineWinner(pA, pB, totalDrivePA, totalDrivePB);
            notifyVictory(gameState, winner, lane);
        }
    }

    private PlayerState determineWinner(PlayerState pA, PlayerState pB, Drive powerA, Drive powerB) {
        return powerA.value() > powerB.value() ? pA : pB;
    }

    private Drive calculateTotalDrive(GameState game,PlayerState player, CardLocation location) {
    int totalDrive = player.getUnitsIn(location).stream()
                    .mapToInt( card -> card.getEffectiveDrive(game).value())
                    .sum();

    return Drive.of(totalDrive);
    }

    private void applyDamage(GameState game,Drive totalDrive, PlayerState targetedPlayer, CardLocation location) {
        List<Card> targets = new ArrayList<>(targetedPlayer.getUnitsIn(location));

        targets.sort(Comparator.comparingInt(c -> c.getDrive().value()));

        int remainingDamage = totalDrive.value();
        for (Card target : targets) {
            if (remainingDamage <= 0) { break; }

            if (remainingDamage >= target.getDrive().value()) {
                remainingDamage -= target.getDrive().value();
                target.takeDamage(game, Drive.of(target.getDrive().value()));
                targetedPlayer.destroyCard(game, target);
            } else {
                target.takeDamage(game, Drive.of(remainingDamage));
                remainingDamage = 0;
            }
        }

    }

    public boolean hasFieldUnitsFromBothPlayers(PlayerState pA, PlayerState pB, CardLocation lane) {
        return pA.hasUnitsIn(lane) && pB.hasUnitsIn(lane);
    }

    private void notifyVictory(GameState game,PlayerState winner, CardLocation lane) {
        winner.getUnitsIn(lane).forEach(c -> c.onCombatWin(game, winner, lane));
    }
}
