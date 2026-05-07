package com.saroga.timewarp.domain.model.effect.action;

import com.saroga.timewarp.domain.model.entity.card.Card;
import java.util.List;

public interface Action {
    // source = la carta que lanza el efecto. targets = a quién va dirigido
    void execute(Card source, List<Card> targets);
}
