package com.saroga.timewarp.infrastructure.persistence.jpa.mapper;

import com.saroga.timewarp.application.factory.EffectFactory;
import com.saroga.timewarp.domain.model.entity.cardTemplate.CardTemplate;
import com.saroga.timewarp.domain.model.enums.CardType;
import com.saroga.timewarp.domain.model.enums.Keyword;
import com.saroga.timewarp.domain.model.enums.SpellSpeed;
import com.saroga.timewarp.domain.model.enums.TargetGroup;
import com.saroga.timewarp.domain.model.vo.card.Cost;
import com.saroga.timewarp.domain.model.vo.card.Drive;
import com.saroga.timewarp.domain.model.vo.card.TargetingRequirement;
import com.saroga.timewarp.infrastructure.persistence.jpa.entity.CardTemplate.CardTemplateEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class CardTemplateMapper {

    private final EffectFactory effectFactory;

    public CardTemplateMapper(EffectFactory effectFactory) {
        this.effectFactory = effectFactory;
    }

    public CardTemplate toDomain (CardTemplateEntity entity) {
        return new CardTemplate(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                Drive.of(entity.getBaseDrive()),
                Cost.of(entity.getBaseCost()),
                CardType.valueOf(entity.getCardType()),
                SpellSpeed.valueOf(entity.getSpellSpeed()),
                mapTargeting(entity),
                mapKeywords(entity.getKeywords()),
                effectFactory.createEffects(entity.getEffects())
        );
    }

    private List<TargetingRequirement> mapTargeting(CardTemplateEntity entity) {
        List<TargetingRequirement> targets = new ArrayList<>();
        if (entity.getTargetGroup() != null && !entity.getTargetGroup().equals("NONE")) {
            targets.add(new TargetingRequirement(
                    entity.getTargetCount(),
                    TargetGroup.valueOf(entity.getTargetGroup())
            ));
        }
        return targets;
    }

    private List<Keyword> mapKeywords(String keywordsStr) {
        if (keywordsStr == null || keywordsStr.isBlank() || keywordsStr.equals("NONE")) {
            return new ArrayList<>();
        }
        return Arrays.stream(keywordsStr.split(","))
                .map(String::trim)
                .map(Keyword::valueOf)
                .collect(Collectors.toList());
    }
}
