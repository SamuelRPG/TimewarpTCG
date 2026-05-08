package com.saroga.timewarp.infrastructure.persistence.jpa.entity.CardTemplate;

import com.saroga.timewarp.domain.model.effect.effectInterface.Effect;
import com.saroga.timewarp.domain.model.enums.Keyword;
import com.saroga.timewarp.domain.model.enums.SpellSpeed;
import com.saroga.timewarp.domain.model.vo.card.TargetingRequirement;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "card_templates")
@Data
public class CardTemplateEntity {
    @Id
    private String id;
    private String name;
    private String description;
    private int baseDrive;
    private int baseCost;
    private String cardType;
    private String spellSpeed;
    private String targets;
    private String keywords;
    private String effects;
    private String faction;
}
