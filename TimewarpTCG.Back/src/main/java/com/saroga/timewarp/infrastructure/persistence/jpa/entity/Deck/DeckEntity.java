package com.saroga.timewarp.infrastructure.persistence.jpa.entity.Deck;

import com.saroga.timewarp.infrastructure.persistence.jpa.entity.DeckCard.DeckCardEntity;
import com.saroga.timewarp.infrastructure.persistence.jpa.entity.User.UserEntity;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "decks")
public class DeckEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    // Relación con las cartas. CascadeType.ALL guarda las cartas automáticamente al guardar el mazo
    @OneToMany(mappedBy = "deck", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DeckCardEntity> cards = new ArrayList<>();

    // Constructores, Getters y Setters
    public DeckEntity() {}

    public UUID getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public UserEntity getUser() { return user; }
    public void setUser(UserEntity user) { this.user = user; }
    public List<DeckCardEntity> getCards() { return cards; }

    public void addCard(DeckCardEntity card) {
        cards.add(card);
        card.setDeck(this);
    }
}
