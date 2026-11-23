// Ayudas de reglas del LADO CLIENTE. Reflejan lo que hacen CardPlayService / PlayerState / GameFlowService
// de tu backend, pero SOLO para dar pistas visuales (resaltar zonas, avisar del motivo).
// La autoridad es siempre el servidor: el cliente nunca bloquea una acción por estas pistas.
using System;
using System.Collections.Generic;
using System.Text.RegularExpressions;

namespace TimewarpTCG
{
    public enum Zone { Base, Left, Right }

    public struct TargetReq
    {
        public int count;
        public string group;
        public TargetReq(int c, string g) { count = c; group = g; }
    }

    public static class GameRules
    {
        public const int MaxUnitsPerZone = 6;     // PlayerState.validateMovement / CardPlayService

        // ------------------------------------------------------------ zonas

        public static string ToDestination(Zone z)
        {
            switch (z) { case Zone.Left: return "FIELD_LEFT"; case Zone.Right: return "FIELD_RIGHT"; default: return "MY_BASE"; }
        }

        public static string ZoneName(Zone z)
        {
            switch (z) { case Zone.Left: return "carril izquierdo"; case Zone.Right: return "carril derecho"; default: return "base"; }
        }

        public static List<CardDto> CardsIn(PlayerDto p, Zone z)
        {
            List<CardDto> l = z == Zone.Left ? p.fieldLeft : z == Zone.Right ? p.fieldRight : p.baseZone;
            return l ?? new List<CardDto>();
        }

        public static List<CardDto> Board(PlayerDto p)
        {
            var all = new List<CardDto>();
            all.AddRange(CardsIn(p, Zone.Base));
            all.AddRange(CardsIn(p, Zone.Left));
            all.AddRange(CardsIn(p, Zone.Right));
            return all;
        }

        /// <summary>Un carril está "disputado" cuando AMBOS jugadores tienen unidades en él (GameState.isBattlefieldContested).</summary>
        public static bool Contested(GameStateDto s, Zone lane)
        {
            return CardsIn(s.activePlayer, lane).Count > 0 && CardsIn(s.opponent, lane).Count > 0;
        }

        public static bool FindOnBoard(GameStateDto s, string id, out CardDto card, out bool mine, out Zone zone)
        {
            foreach (var who in new[] { true, false })
            {
                var p = who ? s.activePlayer : s.opponent;
                foreach (Zone z in new[] { Zone.Base, Zone.Left, Zone.Right })
                    foreach (var c in CardsIn(p, z))
                        if (c.id == id) { card = c; mine = who; zone = z; return true; }
            }
            card = null; mine = false; zone = Zone.Base; return false;
        }

        // ------------------------------------------------------------ cartas

        public static bool IsUnit(CardDto c) { return c.cardType == "UNIT"; }
        public static bool IsSpell(CardDto c) { return c.cardType == "SPELL"; }

        /// <summary>true si el servidor (parcheado) nos manda keywords/velocidad. Si no, no podemos afirmar nada.</summary>
        public static bool KeywordsKnown(CardDto c) { return !string.IsNullOrEmpty(c.spellSpeed); }

        public static bool HasKeyword(CardDto c, string kw)
        {
            return c.keywords != null && c.keywords.Contains(kw);
        }

        static readonly Regex ReqRegex = new Regex(@"requiredCount\s*=\s*(\d+)\s*,\s*targetGroup\s*=\s*([A-Z_]+)");
        static readonly Regex ReqRegex2 = new Regex(@"^\s*(\d+)\s*:\s*([A-Z_]+)\s*$");

        /// <summary>Requisitos de objetivos de la carta (versión estructurada del parche o, si no, parseando el texto antiguo).</summary>
        public static List<TargetReq> Requirements(CardDto c)
        {
            var res = new List<TargetReq>();
            if (c.requirements != null && c.requirements.Count > 0)
            {
                foreach (var r in c.requirements) res.Add(new TargetReq(r.count, r.group));
                return res;
            }
            if (c.targetingRequirements != null)
            {
                foreach (var t in c.targetingRequirements)
                {
                    var m = ReqRegex.Match(t ?? "");
                    if (!m.Success) m = ReqRegex2.Match(t ?? "");
                    if (m.Success) res.Add(new TargetReq(int.Parse(m.Groups[1].Value), m.Groups[2].Value));
                }
            }
            return res;
        }

        public static int TotalTargets(CardDto c)
        {
            int n = 0;
            foreach (var r in Requirements(c)) n += r.count;
            return n;
        }

        public static string GroupName(string g)
        {
            switch (g)
            {
                case "ALLY": return "aliada";
                case "ENEMY": return "enemiga";
                case "ANY_UNIT": return "cualquiera";
                case "GLOBAL": return "global";
                case "LANE": return "carril";
                default: return g;
            }
        }

        public static string RequirementsText(CardDto c)
        {
            var reqs = Requirements(c);
            if (reqs.Count == 0) return "";
            var parts = new List<string>();
            foreach (var r in reqs) parts.Add(r.count + " unidad " + GroupName(r.group));
            return string.Join(" + ", parts.ToArray());
        }

        /// <summary>¿Puede ser objetivo ese candidato para ese grupo? (CardPlayService.matchesGroup)</summary>
        public static bool IsValidTarget(string group, bool candidateIsMine)
        {
            switch (group)
            {
                case "ALLY": return candidateIsMine;
                case "ENEMY": return !candidateIsMine;
                case "ANY_UNIT": return true;
                default: return false;   // GLOBAL / LANE: el backend actual los rechaza (matchesGroup -> default false)
            }
        }

        public static string FactionName(string f)
        {
            switch (f)
            {
                case "ANCIENT_EGYPT": return "Antiguo Egipto";
                case "ROMANS": return "Romanos";
                case "STEAMPUNK": return "Steampunk";
                case "GLITCHPOP": return "Glitchpop";
                default: return f ?? "";
            }
        }

        public static string SpeedName(string s)
        {
            switch (s)
            {
                case "SLOW": return "LENTO (solo en tu fase principal)";
                case "FAST": return "RÁPIDO (va a la pila, se puede responder)";
                case "INSTANT": return "INSTANTÁNEO (se resuelve al momento)";
                default: return "";
            }
        }

        // ------------------------------------------------------------ estado de la partida

        public static bool Patched(GameStateDto s) { return !string.IsNullOrEmpty(s.gameStatus); }

        /// <summary>Fin de partida: GameState.checkWinCondition = alguien con ≥10 puntos Y ≥2 de ventaja.</summary>
        public static bool IsOver(GameStateDto s)
        {
            if (s.gameStatus == "FINISHED") return true;
            if (s.gameStatus == "ONGOING") return false;
            int a = s.activePlayer.score, b = s.opponent.score;      // servidor sin parchear: lo deducimos
            return Math.Max(a, b) >= 10 && Math.Abs(a - b) >= 2;
        }

        public static bool IWon(GameStateDto s)
        {
            if (!string.IsNullOrEmpty(s.winnerId)) return s.winnerId == s.activePlayer.id;
            return s.activePlayer.score > s.opponent.score;
        }

        public static HashSet<string> PendingIds(GameStateDto s, HashSet<string> local)
        {
            if (Patched(s))
                return new HashSet<string>(s.activePlayer.pendingMoveIds ?? new List<string>());
            return local ?? new HashSet<string>();
        }

        public static string PhaseName(string phase)
        {
            switch (phase)
            {
                case "MAIN_OPEN": return "FASE PRINCIPAL";
                case "MAIN_CLOSED": return "PRINCIPAL · PILA ACTIVA";
                case "COMBAT_OPEN": return "COMBATE PENDIENTE";
                case "COMBAT_CLOSED": return "COMBATE · PILA ACTIVA";
                default: return phase ?? "";
            }
        }

        public static string PhaseHelp(string phase)
        {
            switch (phase)
            {
                case "MAIN_OPEN":
                    return "Puedes bajar unidades a tu base, lanzar hechizos y mover unidades listas. Si los dos pasáis seguidos sin nada en la pila, termina el turno y se puntúa.";
                case "MAIN_CLOSED":
                    return "Hay hechizos en la pila: solo se responde con FAST o INSTANT (no unidades). Si los dos pasáis seguidos, la pila se resuelve (el último lanzado, primero).";
                case "COMBAT_OPEN":
                    return "Hay un carril disputado. Puedes lanzar FAST/INSTANT o mover unidades. Si los dos pasáis seguidos, se resuelve el combate.";
                case "COMBAT_CLOSED":
                    return "Hay hechizos en la pila durante el combate. Responde con FAST/INSTANT o pasa; si los dos pasáis, se resuelve la pila.";
                default: return "";
            }
        }

        /// <summary>Texto corto de "qué toca hacer ahora".</summary>
        public static string WhatNow(GameStateDto s, HashSet<string> pending)
        {
            if (IsOver(s)) return "La partida ha terminado.";
            var me = s.activePlayer;
            if (!me.hasPriority) return "Esperando al rival…";
            if (pending != null && pending.Count > 0)
                return "Tienes " + pending.Count + " movimiento(s) sin confirmar. Pulsa «Confirmar movimientos» (pasa prioridad) para ejecutarlos.";
            if (s.spellStack != null && s.spellStack.Count > 0)
                return "Hay " + s.spellStack.Count + " cosa(s) en la pila (a la derecha). Solo puedes responder con un hechizo FAST o INSTANT, o pasar. Cuando pasáis los dos seguidos, se resuelve de arriba abajo.";
            return "Tienes la prioridad: juega, mueve o pasa.";
        }

        // ------------------------------------------------------------ pistas de legalidad (null = parece legal)

        public static string PlayHint(GameStateDto s, CardDto c, HashSet<string> pending)
        {
            var me = s.activePlayer;
            if (IsOver(s)) return "La partida ha terminado.";
            if (!me.hasPriority) return "No tienes la prioridad.";
            if (pending != null && pending.Count > 0)
                return "Has movido unidades: pasa prioridad para confirmar los movimientos antes de jugar cartas.";
            if (c.currentCost > me.resource)
                return "Recurso insuficiente (cuesta " + c.currentCost + ", tienes " + me.resource + ").";

            if (IsUnit(c))
            {
                if (s.gamePhase != "MAIN_OPEN")
                    return "Las unidades solo se bajan en la fase principal abierta (sin hechizos en la pila ni combate pendiente).";
                if (CardsIn(me, Zone.Base).Count >= MaxUnitsPerZone)
                    return "Tu base está llena (máximo " + MaxUnitsPerZone + " unidades).";
            }
            else if (IsSpell(c))
            {
                if (c.spellSpeed == "SLOW" && s.gamePhase != "MAIN_OPEN")
                    return "Los hechizos SLOW solo se juegan en la fase principal abierta.";
            }
            return null;
        }

        /// <summary>¿Es razonable jugar esta unidad directamente en un carril? (Keyword DISRUPTION + carril disputado)</summary>
        public static bool LaneLooksPlayable(GameStateDto s, CardDto c, Zone lane)
        {
            if (!IsUnit(c)) return false;
            if (!KeywordsKnown(c)) return Contested(s, lane);     // sin datos: solo exigimos disputa
            return HasKeyword(c, "DISRUPTION") && Contested(s, lane);
        }

        public static string MoveHint(GameStateDto s, CardDto c)
        {
            if (IsOver(s)) return "La partida ha terminado.";
            if (!s.activePlayer.hasPriority) return "No tienes la prioridad.";
            if (StackClosesPhase(s)) return "No puedes mover unidades mientras hay algo en la pila: solo puedes responder con un hechizo FAST o INSTANT, o pasar.";
            if (c.isExhausted) return "Está agotada: se recupera al empezar el siguiente turno.";
            return null;
        }

        /// <summary>Hay algo en la pila (fase cerrada): solo se puede responder con hechizos FAST/INSTANT o pasar.</summary>
        public static bool StackClosesPhase(GameStateDto s)
        {
            return s.gamePhase == "MAIN_CLOSED" || s.gamePhase == "COMBAT_CLOSED" || (s.spellStack != null && s.spellStack.Count > 0);
        }

        /// <summary>Una entrada de la pila como si fuera una carta, para poder dibujarla y verla en grande (los hechizos del rival no están en tu mano).</summary>
        public static CardDto StackEntryToCard(StackEntryDto e)
        {
            return new CardDto
            {
                id = e.cardId, name = e.cardName, description = e.description,
                cardType = string.IsNullOrEmpty(e.cardType) ? "SPELL" : e.cardType,
                currentCost = e.currentCost, faction = e.faction,
                spellSpeed = e.kind == "TRIGGER" ? "NOT_A_SPELL" : e.spellSpeed,
                keywords = e.keywords ?? new List<string>()
            };
        }

        /// <summary>¿Parece legal mover esta unidad a ese destino? (PlayerState.validateMovement)</summary>
        public static bool MoveLooksLegal(GameStateDto s, CardDto c, Zone from, Zone to)
        {
            if (from == to) return false;
            if (c.isExhausted) return false;
            bool fieldToField = from != Zone.Base && to != Zone.Base;
            if (fieldToField && KeywordsKnown(c) && !HasKeyword(c, "JUMPER")) return false;
            if (CardsIn(s.activePlayer, to).Count >= MaxUnitsPerZone) return false;
            return true;
        }
    }
}
