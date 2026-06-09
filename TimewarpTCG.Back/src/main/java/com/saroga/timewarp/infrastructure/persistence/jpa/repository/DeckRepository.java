package com.saroga.timewarp.infrastructure.persistence.jpa.repository;

import com.saroga.timewarp.infrastructure.persistence.jpa.entity.Deck.DeckEntity;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface DeckRepository extends JpaRepository<DeckEntity, UUID> {
    List<DeckEntity> findByUserId(UUID userId);

    @Transactional
    void deleteByUserId(UUID userId);
}
