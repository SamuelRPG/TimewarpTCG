package com.saroga.timewarp.infrastructure.persistence.jpa.repository;

import com.saroga.timewarp.infrastructure.persistence.jpa.entity.CardTemplate.CardTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CardTemplateRepository extends JpaRepository<CardTemplateEntity, String> {
    // crea automáticamente todos los métodos: save, findById, findAll...
}
