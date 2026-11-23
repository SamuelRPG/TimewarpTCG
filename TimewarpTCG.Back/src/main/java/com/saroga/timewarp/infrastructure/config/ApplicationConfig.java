package com.saroga.timewarp.infrastructure.config;

import com.saroga.timewarp.application.factory.DeckFactory;
import com.saroga.timewarp.application.ports.in.PlayerInGameActions.MoveCard.MoveCardUseCase;
import com.saroga.timewarp.application.ports.in.PlayerInGameActions.PassPriority.PassPriorityUseCase;
import com.saroga.timewarp.application.ports.in.PlayerInGameActions.PlayCard.PlayCardUseCase;
import com.saroga.timewarp.application.ports.in.StartMatch.StartMatchUseCase;
import com.saroga.timewarp.application.ports.out.DeckRepository.DeckRepositoryPort; // <-- AÑADIDO
import com.saroga.timewarp.application.service.BotBrain.BotBrainService;
import com.saroga.timewarp.application.service.BotDeck.BotDeckService;
import com.saroga.timewarp.application.service.PlayerInGameActions.MoveCard.MoveCardService;
import com.saroga.timewarp.application.service.PlayerInGameActions.PassPriority.PassPriorityService;
import com.saroga.timewarp.application.service.PlayerInGameActions.PlayCard.PlayCardService;
import com.saroga.timewarp.application.service.StartMatch.StartMatchService;
import com.saroga.timewarp.domain.repository.cardCatalog.CardCatalogRepository;
import com.saroga.timewarp.domain.service.CardPlayService;
import com.saroga.timewarp.domain.service.CombatService;
import com.saroga.timewarp.domain.service.DeckValidatorService;
import com.saroga.timewarp.domain.service.GameFlowService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationConfig {

    // <-- AHORA INYECTAMOS DeckRepositoryPort AQUÍ
    @Bean
    public StartMatchUseCase startMatchUseCase(DeckRepositoryPort deckRepositoryPort, DeckFactory deckFactory, BotDeckService botDeckService) {
        return new StartMatchService(deckRepositoryPort, deckFactory, botDeckService);
    }

    @Bean
    public BotDeckService botDeckService(CardCatalogRepository cardCatalogRepository, DeckFactory deckFactory,
                                         DeckValidatorService deckValidatorService) {
        return new BotDeckService(cardCatalogRepository, deckFactory, deckValidatorService);
    }

    @Bean
    public PlayCardUseCase playCardUseCase(CardPlayService cardPlayService) {
        return new PlayCardService(cardPlayService);
    }

    @Bean
    public MoveCardUseCase moveCardUseCase(GameFlowService gameFlowService, CombatService combatService) {
        return new MoveCardService(gameFlowService, combatService);
    }

    @Bean
    public PassPriorityUseCase passPriorityUseCase(GameFlowService gameFlowService, CombatService combatService) {
        return new PassPriorityService(gameFlowService, combatService);
    }

    @Bean
    public BotBrainService botBrainService(PlayCardUseCase playCardUseCase,
                                           PassPriorityUseCase passPriorityUseCase,
                                           MoveCardUseCase moveCardUseCase) {
        return new BotBrainService(playCardUseCase, moveCardUseCase, passPriorityUseCase);
    }
}