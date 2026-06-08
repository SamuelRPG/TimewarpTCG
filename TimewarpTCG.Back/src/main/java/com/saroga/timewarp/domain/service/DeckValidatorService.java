package com.saroga.timewarp.domain.service;

import com.saroga.timewarp.domain.model.entity.cardTemplate.CardTemplate;
import com.saroga.timewarp.domain.model.entity.deck.Deck;
import com.saroga.timewarp.domain.model.enums.Faction;

import java.util.Set;
import java.util.stream.Collectors;

public class DeckValidatorService {

    public void validate(Deck deck) {
        Set<Faction> uniqueFactions = deck.getTemplates().stream()
                .map(CardTemplate::getFaction)
                .collect(Collectors.toSet());

        if (uniqueFactions.size() > 2) {
            throw new IllegalArgumentException("Mazo inválido: Contiene " + uniqueFactions.size() +
                    " facciones " + uniqueFactions + ". El máximo permitido es 2.");
        }
    }


}
