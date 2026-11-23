// Tu backend ejecuta el turno completo del bot DENTRO de tu petición y solo te manda el estado final.
// Para que el jugador entienda qué ha pasado, comparamos el estado anterior con el nuevo y generamos
// líneas de registro ("Bot juega Muro de Energía", "Infantería: 2 → 0"...).
using System.Collections.Generic;

namespace TimewarpTCG
{
    public static class StateDiff
    {
        const string Me = "<color=#7FB2FF><b>Tú</b></color>";
        /// <summary>Nombre del rival en el registro de la partida: "Bot" contra la maquina o el nombre de la persona online.</summary>
        public static string OpponentName = "Bot";
        static string Bot { get { return "<color=#FF8A80><b>" + OpponentName + "</b></color>"; } }

        struct Seen
        {
            public CardDto card; public bool mine; public bool inHand; public Zone zone;
        }

        static string Who(bool mine) { return mine ? Me : Bot; }

        static string ZoneLabel(Zone z)
        {
            switch (z) { case Zone.Left: return "carril izquierdo"; case Zone.Right: return "carril derecho"; default: return "base"; }
        }

        static void Index(GameStateDto s, Dictionary<string, Seen> map)
        {
            foreach (var who in new[] { true, false })
            {
                var p = who ? s.activePlayer : s.opponent;
                if (p == null) continue;
                foreach (Zone z in new[] { Zone.Base, Zone.Left, Zone.Right })
                    foreach (var c in GameRules.CardsIn(p, z))
                        map[c.id] = new Seen { card = c, mine = who, inHand = false, zone = z };
                if (who && p.hand != null)
                    foreach (var c in p.hand)
                        map[c.id] = new Seen { card = c, mine = true, inHand = true, zone = Zone.Base };
            }
        }

        // ====================================================================== eventos estructurados (para los efectos visuales)

        public enum EventKind { Played, Moved, Destroyed, DriveChanged, Score, Turn, CombatResolved, SpellOnStack, GameOver }

        public sealed class GameEvent
        {
            public EventKind kind;
            public string cardId, cardName;
            public bool mine;
            public int a, b;       // DriveChanged: a=antes b=después | Score: a=puntos tuyos ganados b=del bot | Turn: a=nº de turno
            public Zone zone;
        }

        /// <summary>Qué ha cambiado entre dos estados, de forma estructurada (misma lógica que Describe, pero con datos en vez de texto).</summary>
        public static List<GameEvent> Events(GameStateDto prev, GameStateDto next)
        {
            var ev = new List<GameEvent>();
            if (prev == null) return ev;

            var before = new Dictionary<string, Seen>();
            var after = new Dictionary<string, Seen>();
            Index(prev, before);
            Index(next, after);

            foreach (var kv in after)
            {
                var a = kv.Value;
                if (a.inHand) continue;
                Seen b;
                bool known = before.TryGetValue(kv.Key, out b);
                if (!known || b.inHand)
                    ev.Add(new GameEvent { kind = EventKind.Played, cardId = kv.Key, cardName = a.card.name, mine = a.mine, zone = a.zone });
                else if (b.zone != a.zone)
                    ev.Add(new GameEvent { kind = EventKind.Moved, cardId = kv.Key, cardName = a.card.name, mine = a.mine, zone = a.zone });
                else if (b.card.currentDrive != a.card.currentDrive)
                    ev.Add(new GameEvent { kind = EventKind.DriveChanged, cardId = kv.Key, cardName = a.card.name, mine = a.mine, a = b.card.currentDrive, b = a.card.currentDrive, zone = a.zone });
            }
            foreach (var kv in before)
            {
                var b = kv.Value;
                if (b.inHand) continue;
                if (!after.ContainsKey(kv.Key))
                    ev.Add(new GameEvent { kind = EventKind.Destroyed, cardId = kv.Key, cardName = b.card.name, mine = b.mine, a = b.card.currentDrive, zone = b.zone });
            }

            var prevIds = new HashSet<string>();
            if (prev.spellStack != null) foreach (var e in prev.spellStack) prevIds.Add(e.cardId);
            if (next.spellStack != null)
                foreach (var e in next.spellStack)
                    if (!prevIds.Contains(e.cardId))
                        ev.Add(new GameEvent { kind = EventKind.SpellOnStack, cardId = e.cardId, cardName = e.cardName, mine = e.ownerId == next.activePlayer.id });

            bool wasCombat = prev.gamePhase == "COMBAT_OPEN" || prev.gamePhase == "COMBAT_CLOSED";
            if (wasCombat && next.gamePhase == "MAIN_OPEN" && next.turn == prev.turn)
                ev.Add(new GameEvent { kind = EventKind.CombatResolved });

            int dMe = next.activePlayer.score - prev.activePlayer.score;
            int dBot = next.opponent.score - prev.opponent.score;
            if (next.turn != prev.turn) ev.Add(new GameEvent { kind = EventKind.Turn, a = next.turn });
            if (dMe != 0 || dBot != 0) ev.Add(new GameEvent { kind = EventKind.Score, a = dMe, b = dBot });
            if (GameRules.IsOver(next) && !GameRules.IsOver(prev)) ev.Add(new GameEvent { kind = EventKind.GameOver, mine = GameRules.IWon(next) });
            return ev;
        }

        public static List<string> Describe(GameStateDto prev, GameStateDto next)
        {
            var lines = new List<string>();

            if (prev == null)
            {
                lines.Add("<b>¡Empieza la partida!</b> Robas 4 cartas. Primer turno: recurso " + next.activePlayer.resource + ".");
                foreach (var c in GameRules.Board(next.opponent))
                    lines.Add(Bot + " juega <b>" + c.name + "</b> en su base.");
                return lines;
            }

            var before = new Dictionary<string, Seen>();
            var after = new Dictionary<string, Seen>();
            Index(prev, before);
            Index(next, after);

            // --- jugadas y movimientos (lo que ahora está en el tablero)
            foreach (var kv in after)
            {
                var a = kv.Value;
                if (a.inHand) continue;
                Seen b;
                if (!before.TryGetValue(kv.Key, out b))
                {
                    lines.Add(Who(a.mine) + " juega <b>" + a.card.name + "</b> en " + ZoneLabel(a.zone) + ".");
                }
                else if (b.inHand)
                {
                    lines.Add(Who(a.mine) + " juega <b>" + a.card.name + "</b> (coste " + a.card.currentCost + ") en " + ZoneLabel(a.zone) + ".");
                }
                else if (b.zone != a.zone)
                {
                    lines.Add(Who(a.mine) + " mueve <b>" + a.card.name + "</b>: " + ZoneLabel(b.zone) + " → " + ZoneLabel(a.zone) + ".");
                }
            }

            // --- hechizos que has lanzado (salen de tu mano y no aparecen en el tablero)
            foreach (var kv in before)
            {
                var b = kv.Value;
                if (!b.inHand || !b.mine) continue;
                Seen a;
                bool stillThere = after.TryGetValue(kv.Key, out a);
                if (!stillThere) lines.Add(Me + " lanzas <b>" + b.card.name + "</b>.");
            }

            // --- bajas
            int oppDestroyed = 0;
            foreach (var kv in before)
            {
                var b = kv.Value;
                if (b.inHand) continue;
                if (!after.ContainsKey(kv.Key))
                {
                    lines.Add("<color=#FFB74D>x</color> <b>" + b.card.name + "</b> de " + (b.mine ? "Tú" : OpponentName) + " es destruida (" + b.card.currentDrive + " de Drive).");
                    if (!b.mine) oppDestroyed++;
                }
            }

            // --- cambios de Drive (daño, bonificaciones)
            foreach (var kv in after)
            {
                var a = kv.Value;
                Seen b;
                if (a.inHand) continue;
                if (before.TryGetValue(kv.Key, out b) && !b.inHand && b.card.currentDrive != a.card.currentDrive)
                {
                    string col = a.card.currentDrive > b.card.currentDrive ? "#81C784" : "#FF8A80";
                    lines.Add("   <b>" + a.card.name + "</b> (" + (a.mine ? "Tú" : OpponentName) + "): Drive <color=" + col + ">" + b.card.currentDrive + " → " + a.card.currentDrive + "</color>");
                }
            }

            // --- hechizos del bot (cementerio sube más de lo que explican las bajas)
            int graveDelta = next.opponent.graveyardSize - prev.opponent.graveyardSize;
            if (graveDelta - oppDestroyed > 0)
                lines.Add(Bot + " ha usado " + (graveDelta - oppDestroyed) + " hechizo(s) (su cementerio sube).");

            // --- pila
            int pPrev = prev.spellStack != null ? prev.spellStack.Count : 0;
            int pNext = next.spellStack != null ? next.spellStack.Count : 0;
            if (next.spellStack != null)
            {
                var prevIds = new HashSet<string>();
                if (prev.spellStack != null) foreach (var e in prev.spellStack) prevIds.Add(e.cardId);
                foreach (var e in next.spellStack)
                    if (!prevIds.Contains(e.cardId))
                        lines.Add(e.kind == "TRIGGER"
                            ? "   El efecto al entrar de «" + e.cardName + "» va a la pila (" + (e.ownerId == next.activePlayer.id ? "Tú" : OpponentName) + ")."
                            : "   «" + e.cardName + "» entra en la pila (" + (e.ownerId == next.activePlayer.id ? "Tú" : OpponentName) + ").");
            }
            if (pPrev > 0 && pNext == 0) lines.Add("   La pila se resuelve.");

            // --- combate
            bool wasCombat = prev.gamePhase == "COMBAT_OPEN" || prev.gamePhase == "COMBAT_CLOSED";
            if (wasCombat && next.gamePhase == "MAIN_OPEN" && next.turn == prev.turn)
                lines.Add("<color=#FFD54F>» Se resuelve el combate.</color>");
            else if (prev.gamePhase != next.gamePhase && next.turn == prev.turn)
                lines.Add("   Fase: " + GameRules.PhaseName(prev.gamePhase) + " → <b>" + GameRules.PhaseName(next.gamePhase) + "</b>");

            // --- turno y puntos
            int dMe = next.activePlayer.score - prev.activePlayer.score;
            int dBot = next.opponent.score - prev.opponent.score;
            if (next.turn != prev.turn)
            {
                lines.Add("<b>══ TURNO " + next.turn + " ══</b>  (recurso " + next.activePlayer.resource + ")");
            }
            if (dMe != 0 || dBot != 0)
            {
                lines.Add("<color=#FFD54F>* Puntos</color>: Tú " + next.activePlayer.score + (dMe > 0 ? " (+" + dMe + ")" : "")
                          + " · " + OpponentName + " " + next.opponent.score + (dBot > 0 ? " (+" + dBot + ")" : ""));
            }

            if (GameRules.IsOver(next) && !GameRules.IsOver(prev))
                lines.Add(GameRules.IWon(next) ? "<b><color=#81C784>¡VICTORIA!</color></b>" : "<b><color=#FF8A80>DERROTA</color></b>");

            return lines;
        }
    }
}
