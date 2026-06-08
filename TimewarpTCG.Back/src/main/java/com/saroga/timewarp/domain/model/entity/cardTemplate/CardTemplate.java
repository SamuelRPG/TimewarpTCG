package com.saroga.timewarp.domain.model.entity.cardTemplate;

import com.saroga.timewarp.domain.model.effect.effectInterface.Effect;
import com.saroga.timewarp.domain.model.enums.CardType;
import com.saroga.timewarp.domain.model.enums.Faction;
import com.saroga.timewarp.domain.model.enums.Keyword;
import com.saroga.timewarp.domain.model.enums.SpellSpeed;
import com.saroga.timewarp.domain.model.vo.card.Cost;
import com.saroga.timewarp.domain.model.vo.card.Drive;
import com.saroga.timewarp.domain.model.vo.card.TargetingRequirement;
import lombok.Value;

import java.util.List;

@Value
public class CardTemplate {
    private String id;
    private String name;
    private String description;
    private Drive baseDrive;
    private Cost baseCost;
    private CardType cardType;
    private SpellSpeed spellSpeed;
    private List<TargetingRequirement> targetingRequirements;
    private List<Keyword> keywords;
    private List<Effect> effects;
    private Faction faction;

    public Faction getFaction() {
        return faction;
    }
}
