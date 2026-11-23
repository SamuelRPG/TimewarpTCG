// Lógica del EDITOR DE MAZOS (sin interfaz, para poder probarla): el mazo que se está editando y sus reglas.
// Son las mismas que valida el servidor (DeckService): 40 cartas exactas, máx. 3 copias de cada una, máx. 2 facciones.
using System;
using System.Collections.Generic;
using System.Linq;

namespace TimewarpTCG
{
    public sealed class DeckDraft
    {
        public const int Size = 40;
        public const int MaxCopies = 3;
        public const int MaxFactions = 2;
        public const int MaxNameLength = 30;

        readonly Dictionary<string, CardDto> _catalog = new Dictionary<string, CardDto>();
        readonly Dictionary<string, int> _counts = new Dictionary<string, int>();

        public string Name = "";
        /// <summary>Nombre con el que ya existe guardado (null si es un mazo nuevo). Sirve para saber si "Guardar" sustituye.</summary>
        public string SavedAs;

        public DeckDraft(IEnumerable<CardDto> catalog)
        {
            foreach (var c in catalog) _catalog[c.id] = c;
        }

        public int Total { get { return _counts.Values.Sum(); } }
        public int CountOf(string cardId) { int n; return _counts.TryGetValue(cardId, out n) ? n : 0; }

        public List<string> Factions()
        {
            return _counts.Where(kv => kv.Value > 0).Select(kv => _catalog[kv.Key].faction).Distinct().OrderBy(f => f).ToList();
        }

        public bool CanAdd(CardDto card, out string reason)
        {
            reason = null;
            if (card == null || !_catalog.ContainsKey(card.id)) { reason = "Carta desconocida."; return false; }
            if (Total >= Size) { reason = "El mazo ya tiene " + Size + " cartas. Quita alguna para añadir otra."; return false; }
            if (CountOf(card.id) >= MaxCopies) { reason = "Máximo " + MaxCopies + " copias de «" + card.name + "»."; return false; }
            var fs = Factions();
            if (!fs.Contains(card.faction) && fs.Count >= MaxFactions)
            {
                reason = "Solo puedes mezclar " + MaxFactions + " facciones: ya llevas " + string.Join(" y ", fs.Select(GameRules.FactionName).ToArray()) + ".";
                return false;
            }
            return true;
        }

        public bool Add(CardDto card, out string reason)
        {
            if (!CanAdd(card, out reason)) return false;
            _counts[card.id] = CountOf(card.id) + 1;
            return true;
        }

        public bool Remove(string cardId)
        {
            int n = CountOf(cardId);
            if (n <= 0) return false;
            if (n == 1) _counts.Remove(cardId); else _counts[cardId] = n - 1;
            return true;
        }

        public void Clear() { _counts.Clear(); Name = ""; SavedAs = null; }

        /// <summary>Carga un mazo guardado. Devuelve cuántas cartas se omitieron por no existir ya en el catálogo.</summary>
        public int Load(DeckDto deck)
        {
            Clear();
            Name = deck.name; SavedAs = deck.name;
            int skipped = 0;
            foreach (var e in deck.cards)
            {
                if (!_catalog.ContainsKey(e.cardId)) { skipped += e.quantity; continue; }
                _counts[e.cardId] = Math.Min(MaxCopies, e.quantity);
            }
            return skipped;
        }

        /// <summary>null si el mazo se puede guardar; si no, el motivo en español.</summary>
        public string Validate()
        {
            string n = (Name ?? "").Trim();
            if (n.Length == 0) return "Ponle un nombre al mazo.";
            if (n.Length > MaxNameLength) return "El nombre no puede superar " + MaxNameLength + " caracteres.";
            string upper = n.ToUpperInvariant();
            if (upper == "AGGRO" || upper == "CONTROL" || upper == "RANDOM" || upper == "ALEATORIO") return "«" + n + "» es un nombre reservado.";
            int t = Total;
            if (t < Size) return "Faltan " + (Size - t) + " cartas para llegar a " + Size + ".";
            if (t > Size) return "Sobran " + (t - Size) + " cartas.";
            var fs = Factions();
            if (fs.Count > MaxFactions) return "Máximo " + MaxFactions + " facciones (llevas " + fs.Count + ").";
            return null;
        }

        public List<DeckEntryDto> ToEntries()
        {
            return _counts.Where(kv => kv.Value > 0).Select(kv => new DeckEntryDto { cardId = kv.Key, quantity = kv.Value }).ToList();
        }

        /// <summary>Cartas del mazo con su número de copias, ordenadas por coste y nombre.</summary>
        public List<KeyValuePair<CardDto, int>> Cards()
        {
            return _counts.Where(kv => kv.Value > 0)
                .Select(kv => new KeyValuePair<CardDto, int>(_catalog[kv.Key], kv.Value))
                .OrderBy(kv => kv.Key.currentCost).ThenBy(kv => kv.Key.name).ToList();
        }

        /// <summary>Curva de coste: posiciones 0..7 (la 7 agrupa "7 o más"), con el nº de cartas de cada coste (no cuenta el coste 0).</summary>
        public int[] CostCurve()
        {
            var curve = new int[8];
            foreach (var kv in _counts)
            {
                int cost = Math.Max(0, Math.Min(7, _catalog[kv.Key].currentCost));
                curve[cost] += kv.Value;
            }
            return curve;
        }

        public int Units() { return _counts.Where(kv => GameRules.IsUnit(_catalog[kv.Key])).Sum(kv => kv.Value); }
        public int Spells() { return Total - Units(); }
    }
}
