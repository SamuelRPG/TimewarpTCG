package com.saroga.timewarp.infrastructure.web.controller;

import com.saroga.timewarp.application.ports.out.DeckRepository.StoredDeck;
import com.saroga.timewarp.application.service.Deck.DeckService;
import com.saroga.timewarp.domain.model.entity.cardTemplate.CardTemplate;
import com.saroga.timewarp.domain.model.enums.Faction;
import com.saroga.timewarp.domain.repository.cardCatalog.CardCatalogRepository;
import com.saroga.timewarp.infrastructure.security.AuthInterceptor;
import com.saroga.timewarp.infrastructure.security.AuthPrincipal;
import com.saroga.timewarp.infrastructure.web.dto.request.SaveDeckRequestDto;
import com.saroga.timewarp.infrastructure.web.dto.response.CatalogCardDto;
import com.saroga.timewarp.infrastructure.web.dto.response.DeckDto;
import com.saroga.timewarp.infrastructure.web.dto.response.DeckEntryDto;
import com.saroga.timewarp.infrastructure.web.dto.response.TargetReqDto;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/menu")
public class MenuController {

    private final CardCatalogRepository cardCatalogRepository;
    private final DeckService deckService;

    public MenuController(CardCatalogRepository cardCatalogRepository, DeckService deckService) {
        this.cardCatalogRepository = cardCatalogRepository;
        this.deckService = deckService;
    }

    // Unity llama aquí para pintar la colección (antes devolvia 500: Jackson no sabe serializar los Effect)
    @GetMapping("/cards")
    public List<CatalogCardDto> getAllCards() {
        return cardCatalogRepository.findAll().stream()
                .map(MenuController::toCatalogDto)
                .sorted(Comparator.comparing((CatalogCardDto c) -> c.faction)
                        .thenComparingInt(c -> c.currentCost)
                        .thenComparing(c -> c.id))
                .collect(Collectors.toList());
    }

    // Mazos guardados del usuario AUTENTICADO (el usuario sale del token, no de un parametro: nadie puede ver los mazos de otro)
    @GetMapping("/decks")
    public ResponseEntity<?> getDecks(HttpServletRequest request) {
        AuthPrincipal me = AuthInterceptor.require(request);
        if (me.guest()) return ResponseEntity.ok(List.of());
        List<DeckDto> decks = deckService.getUserDecks(me.userId()).stream()
                .map(MenuController::toDeckDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(decks);
    }

    // Unity llama aquí al pulsar el botón "Guardar Mazo". Crea el mazo o, si ya existe uno con ese nombre, lo sustituye.
    @PostMapping("/decks")
    public ResponseEntity<?> saveDeck(@RequestBody SaveDeckRequestDto request, HttpServletRequest http) {
        AuthPrincipal me = AuthInterceptor.require(http);
        if (me.guest()) return ResponseEntity.status(403).body(Map.of("error", "Los invitados no pueden guardar mazos. Inicia sesión con tu cuenta."));
        try {
            Map<String, Integer> cards = new LinkedHashMap<>();
            if (request.entries != null) {
                for (DeckEntryDto e : request.entries) {
                    if (e == null || e.cardId == null) continue;
                    cards.merge(e.cardId, e.quantity, Integer::sum);
                }
            } else if (request.cards != null) {
                cards.putAll(request.cards);
            }
            String savedName = deckService.saveCustomDeck(me.userId(), request.deckName, cards);
            StoredDeck saved = deckService.getUserDecks(me.userId()).stream()
                    .filter(d -> d.name().equalsIgnoreCase(savedName)).findFirst().orElseThrow();
            return ResponseEntity.ok(toDeckDto(saved));
        } catch (IllegalArgumentException e) {
            // Si el jugador intentó mezclar 3 facciones, pasarse de 3 copias, etc., Unity recibe este texto
            return ResponseEntity.badRequest().body(Map.of("error", String.valueOf(e.getMessage())));
        }
    }

    @DeleteMapping("/decks")
    public ResponseEntity<?> deleteDeck(@RequestParam("deckName") String deckName, HttpServletRequest http) {
        AuthPrincipal me = AuthInterceptor.require(http);
        if (me.guest()) return ResponseEntity.status(403).body(Map.of("error", "Los invitados no tienen mazos."));
        boolean deleted = deckService.deleteDeck(me.userId(), deckName);
        if (!deleted) return ResponseEntity.status(404).body(Map.of("error", "Mazo no encontrado: " + deckName));
        return ResponseEntity.ok(Map.of("message", "Mazo borrado"));
    }

    // ------------------------------------------------------------------ mapeos
    private static CatalogCardDto toCatalogDto(CardTemplate t) {
        CatalogCardDto d = new CatalogCardDto();
        d.id = t.getId();
        d.name = t.getName();
        d.description = t.getDescription();
        d.cardType = t.getCardType().name();
        d.currentCost = t.getBaseCost().value();
        d.currentDrive = t.getBaseDrive().value();
        d.faction = t.getFaction().name();
        d.keywords = t.getKeywords().stream().map(Enum::name).toList();
        d.spellSpeed = t.getSpellSpeed() != null ? t.getSpellSpeed().name() : "NOT_A_SPELL";
        d.requirements = t.getTargetingRequirements().stream()
                .map(r -> new TargetReqDto(r.requiredCount(), r.targetGroup().name())).toList();
        return d;
    }

    private static DeckDto toDeckDto(StoredDeck stored) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        List<Faction> factions = new ArrayList<>();
        stored.deck().getTemplates().stream()
                .sorted(Comparator.comparing(CardTemplate::getId))
                .forEach(t -> {
                    counts.merge(t.getId(), 1, Integer::sum);
                    if (!factions.contains(t.getFaction())) factions.add(t.getFaction());
                });
        DeckDto dto = new DeckDto();
        dto.name = stored.name();
        dto.total = stored.deck().getTemplates().size();
        dto.factions = factions.stream().map(Enum::name).toList();
        dto.cards = counts.entrySet().stream().map(e -> new DeckEntryDto(e.getKey(), e.getValue())).toList();
        return dto;
    }
}
