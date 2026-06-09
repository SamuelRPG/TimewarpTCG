//package com.saroga.timewarp.application;
//
//import com.saroga.timewarp.application.factory.DeckFactory;
//import com.saroga.timewarp.application.ports.in.PlayerInGameActions.PassPriority.PassPriorityUseCase;
//import com.saroga.timewarp.application.ports.in.PlayerInGameActions.PlayCard.PlayCardUseCase;
//import com.saroga.timewarp.application.ports.in.StartMatch.StartMatchUseCase;
//import com.saroga.timewarp.application.ports.out.GameRepository.GameRepositoryPort;
//import com.saroga.timewarp.application.service.BotBrain.BotBrainService;
//import com.saroga.timewarp.application.service.PlayerInGameActions.PassPriority.PassPriorityService;
//import com.saroga.timewarp.application.service.PlayerInGameActions.PlayCard.PlayCardService;
//import com.saroga.timewarp.application.service.StartMatch.StartMatchService;
//import com.saroga.timewarp.domain.model.entity.card.Card;
//import com.saroga.timewarp.domain.model.entity.gameState.GameState;
//import com.saroga.timewarp.domain.model.enums.CardLocation;
//import com.saroga.timewarp.domain.service.CardPlayService;
//import com.saroga.timewarp.domain.service.CombatService;
//import com.saroga.timewarp.domain.service.GameFlowService;
//import com.saroga.timewarp.infrastructure.persistence.InMemory.InMemoryGameRepository;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//
//import java.util.ArrayList;
//import java.util.UUID;
//
//import static org.junit.jupiter.api.Assertions.*;
//
//public class BotVsHumanIntegrationTest {
//
//    private GameRepositoryPort gameRepository;
//    private StartMatchUseCase startMatchUseCase;
//    private PlayCardUseCase playCardUseCase;
//    private PassPriorityUseCase passPriorityUseCase;
//    private BotBrainService botBrainService;
//
//    @BeforeEach
//    void setUp() {
//        // Inicialización manual de la arquitectura (sin Spring DI para el test)
//        gameRepository = new InMemoryGameRepository();
//
//        GameFlowService gameFlowService = new GameFlowService();
//        CombatService combatService = new CombatService();
//        CardPlayService cardPlayService = new CardPlayService();
//        DeckFactory deckFactory = new DeckFactory();
//
//        startMatchUseCase = new StartMatchService(deckFactory);
//        playCardUseCase = new PlayCardService(cardPlayService);
//        passPriorityUseCase = new PassPriorityService(gameFlowService, combatService);
//
//        // El bot usa los casos de uso para interactuar
//        botBrainService = new BotBrainService(playCardUseCase, null, passPriorityUseCase);
//    }
//
//    @Test
//    void simulateMatchTurn() {
//        // 1. Iniciamos partida
//        UUID humanId = UUID.randomUUID();
//        GameState game = startMatchUseCase.executeLocalMatch(humanId, "AGGRO");
//        gameRepository.save(game);
//
//        System.out.println("--- Partida Iniciada ---");
//        assertTrue(game.getPlayerA().hasPriority(), "El humano debería tener la prioridad al empezar");
//
//        // 2. Acción Humana: Jugar una carta
//        Card cardToPlay = game.getPlayerA().getHand().get(0);
//        playCardUseCase.execute(game, humanId, cardToPlay.getId(), CardLocation.MY_BASE, new ArrayList<>());
//        gameRepository.save(game);
//        System.out.println("Humano juega: " + cardToPlay.getName());
//
//        // ¡IMPORTANTE! Comprobamos que jugar la carta cedió la prioridad automáticamente
//        assertFalse(game.getPlayerA().hasPriority(), "El humano perdió la prioridad al jugar la carta");
//        assertTrue(game.getPlayerB().hasPriority(), "El bot ganó la prioridad");
//
//        // 3. LÓGICA DE "DISPARO" DEL BOT
//        // Como el humano jugó carta, la prioridad pasó al bot. El servidor lo detecta y ejecuta la IA.
//        if (game.getPlayerB().hasPriority()) {
//            System.out.println("Es el turno del Bot. Ejecutando IA...");
//            botBrainService.takeTurn(game);
//            gameRepository.save(game);
//        }
//
//        // 4. Verificaciones Finales
//        assertFalse(game.getPlayerB().hasPriority(), "El bot debería haber terminado su acción y pasado prioridad");
//        assertTrue(game.getPlayerA().hasPriority(), "La prioridad debería haber vuelto al humano tras la acción del bot");
//
//        // Ver si el bot ha bajado algo
//        boolean botHasPlayed = !game.getPlayerB().getBase().isEmpty();
//        System.out.println("¿El bot ha jugado carta?: " + (botHasPlayed ? "SÍ" : "NO"));
//    }
//}
