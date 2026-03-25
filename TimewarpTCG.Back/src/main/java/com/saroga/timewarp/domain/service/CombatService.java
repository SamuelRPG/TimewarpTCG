package com.saroga.timewarp.domain.service;

import com.saroga.timewarp.domain.model.entity.card.Card;
import com.saroga.timewarp.domain.model.entity.gameState.GameState;
import com.saroga.timewarp.domain.model.entity.playerState.PlayerState;
import com.saroga.timewarp.domain.model.enums.CardLocation;
import com.saroga.timewarp.domain.model.enums.Keyword;
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

        Drive totalDrivePA = calculateTotalDrive(pA, lane);
        Drive totalDrivePB = calculateTotalDrive(pB, lane);

        applyDamage(totalDrivePA, pB, lane);
        applyDamage(totalDrivePB, pA, lane);


    }

    private Drive calculateTotalDrive(PlayerState player, CardLocation location) {
    int totalDrive = player.getUnitsIn(location).stream()
                    .mapToInt( card -> {
                        int power = card.getDrive().value();
                        if (card.hasKeyword(Keyword.CHARGE)) power += 1;
                        return power;
                    })
                    .sum();

    return Drive.of(totalDrive);
    }

    private void applyDamage(Drive totalDrive, PlayerState targetedPlayer, CardLocation location) {
        List<Card> targets = new ArrayList<>(targetedPlayer.getUnitsIn(location));

        targets.sort(Comparator.comparingInt(c -> c.getDrive().value()));

        int remainingDamage = totalDrive.value();
        for (Card target : targets) {
            if (remainingDamage <= 0) { break; }

            if (remainingDamage >= target.getDrive().value()) {
                remainingDamage -= target.getDrive().value();
                targetedPlayer.destroyCard(target);
            } else {
                target.takeDamage(Drive.of(remainingDamage));
                remainingDamage = 0;
            }
        }

    }

    private void checkGloryEffect(PlayerState player, CardLocation lane, boolean isWinner) {
        if (isWinner) {
            player.getUnitsIn(lane).stream()
                    .filter(c -> c.hasKeyword(Keyword.GLORY))
                    .forEach(Card::ready);
        }
    }

    public boolean hasFieldUnitsFromBothPlayers(PlayerState pA, PlayerState pB, CardLocation lane) {
        return pA.hasUnitsIn(lane) && pB.hasUnitsIn(lane);
    }
}
