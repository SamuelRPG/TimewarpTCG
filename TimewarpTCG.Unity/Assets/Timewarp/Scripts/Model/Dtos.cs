// Espejo C# de los DTO que envía tu backend (infrastructure/web/dto/**).
// Los campos marcados "backend parcheado" solo existen si aplicas el parche de Backend-Patch/;
// el cliente funciona igualmente sin ellos (los infiere o los ignora).
using System;
using System.Collections.Generic;

namespace TimewarpTCG
{
    [Serializable]
    public class TargetReqDto
    {
        public int count;
        public string group;          // ALLY | ENEMY | ANY_UNIT | GLOBAL | LANE
    }

    [Serializable]
    public class CardDto
    {
        public string id;
        public string name;
        public string description;
        public string cardType;       // UNIT | SPELL
        public int currentDrive;      // "fuerza/vida" de la carta (Drive)
        public int currentCost;
        public bool isExhausted;
        public string faction;        // ANCIENT_EGYPT | ROMANS | STEAMPUNK | GLITCHPOP
        public List<string> targetingRequirements = new List<string>();   // texto "TargetingRequirement[requiredCount=1, targetGroup=ENEMY]"

        // --- backend parcheado ---
        public List<string> keywords = new List<string>();                // DISRUPTION, GLORY, JUMPER, CHARGE
        public string spellSpeed;                                         // NOT_A_SPELL | SLOW | FAST | INSTANT
        public List<TargetReqDto> requirements = new List<TargetReqDto>();
    }

    [Serializable]
    public class PlayerDto
    {
        public string id;
        public int score;
        public int resource;
        public bool hasPriority;
        public int deckSize;
        public int graveyardSize;
        public int handSize;
        public List<CardDto> hand = new List<CardDto>();          // null (vacía) para el rival
        public List<CardDto> baseZone = new List<CardDto>();
        public List<CardDto> fieldLeft = new List<CardDto>();
        public List<CardDto> fieldRight = new List<CardDto>();
        public List<string> pendingMoveIds = new List<string>();  // backend parcheado
    }

    [Serializable]
    public class StackEntryDto                                    // backend parcheado
    {
        public string cardId;
        public string cardName;
        public string ownerId;
        public string spellSpeed;
        public List<string> targetIds = new List<string>();
    }

    [Serializable]
    public class GameStateDto
    {
        public string gameStateId;
        public int turn;
        public string gamePhase;      // MAIN_OPEN | MAIN_CLOSED | COMBAT_OPEN | COMBAT_CLOSED
        public PlayerDto activePlayer;   // OJO: en el backend "activePlayer" = TÚ (el que mira), no "el que tiene el turno"
        public PlayerDto opponent;

        // --- backend parcheado ---
        public string gameStatus;     // ONGOING | FINISHED
        public string winnerId;
        public string consecutivePasses;   // ZERO | ONE | TWO
        public List<StackEntryDto> spellStack = new List<StackEntryDto>();
    }

    [Serializable]
    public class ErrorDto
    {
        public string type;
        public string message;
    }

    // ---------- Peticiones (cliente -> servidor) ----------
    [Serializable] public class StartMatchRequest { public string playerId; public string deckName; public string botDeck; }
    [Serializable] public class PassPriorityRequest { public string gameId; public string playerId; }
    [Serializable] public class MoveCardRequest { public string gameId; public string playerId; public string cardId; public string destination; }
    [Serializable]
    public class PlayCardRequest
    {
        public string gameId; public string playerId; public string cardId; public string destination;
        public List<string> targetIds = new List<string>();
    }

    [Serializable] public class UserDto { public string id; public string username; }

    // ---------- Catálogo y mazos (MenuController) ----------
    // El catálogo reutiliza CardDto: id = id de plantilla (p. ej. "U_AE_01"), currentCost/currentDrive = coste/drive base.
    [Serializable] public class DeckEntryDto { public string cardId; public int quantity; }

    [Serializable]
    public class DeckDto
    {
        public string name;
        public int total;
        public List<string> factions = new List<string>();
        public List<DeckEntryDto> cards = new List<DeckEntryDto>();
    }

    [Serializable]
    public class SaveDeckRequest
    {
        public string userId;
        public string deckName;
        public List<DeckEntryDto> entries = new List<DeckEntryDto>();
    }

    [Serializable] public class ApiError { public string error; }
    // JsonUtility no lee arrays JSON "sueltos": se envuelven como {"items":[...]}
    [Serializable] public class CardList { public List<CardDto> items = new List<CardDto>(); }
    [Serializable] public class DeckList { public List<DeckDto> items = new List<DeckDto>(); }
}
