package com.saroga.timewarp.infrastructure.persistence.jpa.adapter;

import com.saroga.timewarp.domain.model.entity.cardTemplate.CardTemplate;

import com.saroga.timewarp.domain.repository.cardCatalog.CardCatalogRepository;
import com.saroga.timewarp.infrastructure.persistence.jpa.mapper.CardTemplateMapper;
import com.saroga.timewarp.infrastructure.persistence.jpa.repository.CardTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class JpaCardCatalogRepository implements CardCatalogRepository {

    private final CardTemplateRepository jpaRepository;
    private final CardTemplateMapper mapper;

    @Override
    public Optional<CardTemplate> findById(String id) {
        return jpaRepository.findById(id)
                .map(mapper::toDomain);
    }

    @Override
    public List<CardTemplate> findAll() {
        return jpaRepository.findAll().stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }
}