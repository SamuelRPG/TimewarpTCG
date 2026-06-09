package com.saroga.timewarp.infrastructure.config;

import com.saroga.timewarp.application.factory.DeckFactory;
import com.saroga.timewarp.domain.service.CardPlayService;
import com.saroga.timewarp.domain.service.CombatService;
import com.saroga.timewarp.domain.service.DeckValidatorService;
import com.saroga.timewarp.domain.service.GameFlowService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainConfig {

    // cuando se pida un servicio, pasamos por aqui, para no manchar el dominio con arquitectura de spring
    @Bean
    public CardPlayService cardPlayService() {
        return new CardPlayService();
    }

    @Bean
    public CombatService combatService() {
        return new CombatService();
    }

    @Bean
    public GameFlowService gameFlowService() {
        return new GameFlowService();
    }

    @Bean
    public DeckFactory deckFactory() {
        return new DeckFactory();
    }

    @Bean
    public DeckValidatorService deckValidatorService() {
        return new DeckValidatorService();
    }

}