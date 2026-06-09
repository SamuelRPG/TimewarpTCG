package com.saroga.timewarp.infrastructure.persistence.jpa.entity.DeckCard;

import com.saroga.timewarp.infrastructure.persistence.jpa.entity.CardTemplate.CardTemplateEntity;
import com.saroga.timewarp.infrastructure.persistence.jpa.entity.Deck.DeckEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "deck_cards")
public class DeckCardEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deck_id", nullable = false)
    private DeckEntity deck;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_template_id", nullable = false)
    private CardTemplateEntity cardTemplate;

    @Column(nullable = false)
    private int quantity;

    // Constructores, Getters y Setters
    public DeckCardEntity() {}
    public DeckCardEntity(CardTemplateEntity cardTemplate, int quantity) {
        this.cardTemplate = cardTemplate;
        this.quantity = quantity;
    }

    public void setDeck(DeckEntity deck) { this.deck = deck; }
    public CardTemplateEntity getCardTemplate() { return cardTemplate; }
    public int getQuantity() { return quantity; }
}
