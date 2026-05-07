package com.saroga.timewarp.domain.repository.cardCatalog;

import com.saroga.timewarp.domain.model.entity.cardTemplate.CardTemplate;
import java.util.Optional;
import java.util.List;

public interface CardCatalogRepository {
    Optional<CardTemplate> findById(String id);
    List<CardTemplate> findAll();
}
