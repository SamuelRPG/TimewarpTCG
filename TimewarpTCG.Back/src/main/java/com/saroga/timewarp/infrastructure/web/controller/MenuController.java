package com.saroga.timewarp.infrastructure.web.controller;

import com.saroga.timewarp.application.service.Deck.DeckService;
import com.saroga.timewarp.domain.repository.cardCatalog.CardCatalogRepository;
import com.saroga.timewarp.infrastructure.web.dto.request.SaveDeckRequestDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/menu")
@CrossOrigin(origins = "*")
public class MenuController {

    private final CardCatalogRepository cardCatalogRepository;
    private final DeckService deckService;

    public MenuController(CardCatalogRepository cardCatalogRepository, DeckService deckService) {
        this.cardCatalogRepository = cardCatalogRepository;
        this.deckService = deckService;
    }

    // Unity llama aquí para pintar la colección
    @GetMapping("/cards")
    public ResponseEntity<?> getAllCards() {
        return ResponseEntity.ok(cardCatalogRepository.findAll());
    }

    // Unity llama aquí al pulsar el botón "Guardar Mazo"
    @PostMapping("/decks")
    public ResponseEntity<?> saveDeck(@RequestBody SaveDeckRequestDto request) {
        try {
            // Pasamos los datos al servicio validador
            deckService.saveCustomDeck(request.userId, request.deckName, request.cards);
            return ResponseEntity.ok().body("{\"message\": \"Mazo guardado correctamente en Supabase\"}");
        } catch (IllegalArgumentException e) {
            // Si el jugador intentó mezclar 3 facciones, Unity recibe este texto de error
            return ResponseEntity.badRequest().body("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }
}
