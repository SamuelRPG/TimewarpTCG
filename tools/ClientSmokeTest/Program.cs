using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.Linq;
using System.Threading;
using System.Threading.Tasks;
using TimewarpTCG;

// Agente de prueba: juega con las mismas pistas (GameRules) que usará la interfaz de Unity.
static class Program
{
    class Result
    {
        public string Outcome; public int Turn, Actions, ServerErrors, HintTooOptimistic, HintTooStrict, DiffLines, Invariant;
        public string Score; public List<string> Problems = new List<string>();
    }

    static bool Verbose;
    static async Task<int> Main(string[] args)
    {
        Verbose = args.Length > 3 && args[3] == "v";
        string host = args.Length > 0 ? args[0] : "localhost:8080";
        string deck = args.Length > 1 ? args[1] : "AGGRO";
        int games = args.Length > 2 ? int.Parse(args[2]) : 3;
        int bad = 0;
        if (!SelfTests()) { Console.WriteLine("RESULTADO: FALLAN LAS AUTOPRUEBAS"); return 1; }
        var rnd = new Random(1234);
        for (int g = 1; g <= games; g++)
        {
            Result r;
            try { r = await PlayOne(host, deck, rnd); }
            catch (Exception e) { r = new Result { Outcome = "EXCEPCION", Score = "-" }; r.Problems.Add(e.ToString()); }
            bool fail = r.Outcome != "GAMEOVER" && r.Outcome != "STALLED" || r.Problems.Count > 0 || r.Invariant > 0;
            if (fail) bad++;
            Console.WriteLine("partida {0,2} [{1}] {2,-9} turno={3,3} puntos={4,-6} acciones={5,3} erroresServidor={6} pistaOptimista={7} pistaEstricta={8} lineasLog={9} invariantes={10}",
                g, deck, r.Outcome, r.Turn, r.Score, r.Actions, r.ServerErrors, r.HintTooOptimistic, r.HintTooStrict, r.DiffLines, r.Invariant);
            foreach (var p in r.Problems.Take(6)) Console.WriteLine("      ! " + p);
        }
        Console.WriteLine(bad == 0 ? "RESULTADO: OK" : "RESULTADO: " + bad + " partida(s) con problemas");
        return bad == 0 ? 0 : 1;
    }


    // ------------------------------------------------------------------ autopruebas sin servidor
    static bool SelfTests()
    {
        int fails = 0;
        void Check(bool ok, string what) { if (!ok) { fails++; Console.WriteLine("  FALLA autoprueba: " + what); } }

        CardDto Unit(string id, string name, int drive, bool ex = false) { return new CardDto { id = id, name = name, cardType = "UNIT", currentDrive = drive, currentCost = 1, isExhausted = ex, faction = "ROMANS" }; }
        GameStateDto St(int a, int b, string status = null, string winner = null)
        {
            return new GameStateDto { turn = 5, gamePhase = "MAIN_OPEN", gameStatus = status, winnerId = winner,
                activePlayer = new PlayerDto { id = "me", score = a, hasPriority = true, resource = 5 },
                opponent = new PlayerDto { id = "bot", score = b } };
        }

        // fin de partida: servidor sin parchear (se deduce) y parcheado (manda gameStatus/winnerId)
        Check(!GameRules.IsOver(St(9, 3)), "9-3 no termina (nadie llega a 10)");
        Check(!GameRules.IsOver(St(10, 9)), "10-9 no termina (falta ventaja de 2)");
        Check(GameRules.IsOver(St(10, 8)) && GameRules.IWon(St(10, 8)), "10-8 victoria mía");
        Check(GameRules.IsOver(St(4, 11)) && !GameRules.IWon(St(4, 11)), "4-11 derrota");
        Check(GameRules.IsOver(St(10, 3, "FINISHED", "me")) && GameRules.IWon(St(10, 3, "FINISHED", "me")), "parcheado: FINISHED + winnerId yo");
        Check(!GameRules.IsOver(St(10, 3, "ONGOING")), "parcheado: ONGOING manda sobre la deducción");
        Check(GameRules.IsOver(St(2, 10, "FINISHED", "bot")) && !GameRules.IWon(St(2, 10, "FINISHED", "bot")), "parcheado: gana el bot");

        // formato antiguo de objetivos (Record.toString) y el nuevo
        var old = new CardDto { targetingRequirements = new List<string> { "TargetingRequirement[requiredCount=2, targetGroup=ALLY]", "TargetingRequirement[requiredCount=1, targetGroup=ENEMY]" } };
        var r = GameRules.Requirements(old);
        Check(r.Count == 2 && r[0].count == 2 && r[0].group == "ALLY" && r[1].group == "ENEMY" && GameRules.TotalTargets(old) == 3, "parseo de objetivos antiguo");
        var nw = new CardDto { requirements = new List<TargetReqDto> { new TargetReqDto { count = 1, group = "ANY_UNIT" } } };
        Check(GameRules.TotalTargets(nw) == 1, "objetivos estructurados");

        // objetivos válidos según grupo (CardPlayService.matchesGroup)
        Check(GameRules.IsValidTarget("ALLY", true) && !GameRules.IsValidTarget("ALLY", false), "ALLY");
        Check(!GameRules.IsValidTarget("ENEMY", true) && GameRules.IsValidTarget("ENEMY", false), "ENEMY");
        Check(GameRules.IsValidTarget("ANY_UNIT", true) && GameRules.IsValidTarget("ANY_UNIT", false), "ANY_UNIT");
        Check(!GameRules.IsValidTarget("GLOBAL", true), "GLOBAL no soportado por el backend");

        // JSON del servidor SIN parchear (capturado del backend real): los campos nuevos faltan y no debe romper
        string json = "{\"gameStateId\":\"g1\",\"turn\":2,\"gamePhase\":\"MAIN_OPEN\",\"activePlayer\":{\"id\":\"me\",\"score\":0,\"resource\":2,\"hasPriority\":true,\"deckSize\":35,\"graveyardSize\":0,\"handSize\":1,"
                    + "\"hand\":[{\"id\":\"c1\",\"name\":\"Infanter\\u00eda\",\"description\":\"d\",\"cardType\":\"UNIT\",\"currentDrive\":2,\"currentCost\":1,\"isExhausted\":true,\"faction\":\"ANCIENT_EGYPT\",\"targetingRequirements\":[]}],"
                    + "\"baseZone\":[],\"fieldLeft\":[],\"fieldRight\":[]},\"opponent\":{\"id\":\"bot\",\"score\":0,\"resource\":0,\"hasPriority\":false,\"deckSize\":30,\"graveyardSize\":0,\"handSize\":3,\"hand\":null,\"baseZone\":[],\"fieldLeft\":[],\"fieldRight\":[]}}";
        var parsed = Json.FromJson<GameStateDto>(json);
        Check(parsed.activePlayer.hand.Count == 1 && parsed.activePlayer.hand[0].name == "Infanter\u00eda", "parseo JSON sin parchear");
        Check(!GameRules.Patched(parsed) && !GameRules.IsOver(parsed), "sin parchear: Patched=false");

        // diff: daño, destrucción, hechizo del bot, puntos y turno
        var prev = St(0, 0);
        prev.activePlayer.baseZone = new List<CardDto> { Unit("a", "Guardia", 4), Unit("b", "Arquero", 2) };
        prev.opponent.baseZone = new List<CardDto> { Unit("x", "Muro", 4) };
        var next = St(1, 2); next.turn = 6;
        next.activePlayer.baseZone = new List<CardDto> { Unit("a", "Guardia", 1) };       // Guardia herido, Arquero muerto
        next.opponent.baseZone = new List<CardDto> { Unit("x", "Muro", 4), Unit("y", "Nuevo", 3) };
        next.opponent.graveyardSize = 1;
        var lines = string.Join("\n", StateDiff.Describe(prev, next));
        Check(lines.Contains("Arquero") && lines.Contains("destruida"), "diff: unidad destruida");
        Check(lines.Contains("Guardia") && lines.Contains("4 → 1"), "diff: cambio de Drive");
        Check(lines.Contains("Nuevo") && lines.Contains("juega"), "diff: el bot juega una carta");
        Check(lines.Contains("hechizo"), "diff: hechizo del bot por el cementerio");
        Check(lines.Contains("TURNO 6") && lines.Contains("Puntos"), "diff: turno y puntos");

        // pistas de legalidad
        var s2 = St(0, 0); s2.activePlayer.hand = new List<CardDto> { Unit("h1", "Caro", 3) }; s2.activePlayer.hand[0].currentCost = 9;
        Check(GameRules.PlayHint(s2, s2.activePlayer.hand[0], new HashSet<string>()) != null, "pista: recurso insuficiente");
        Check(GameRules.PlayHint(s2, Unit("h2", "Barato", 1), new HashSet<string> { "x" }) != null, "pista: movimientos pendientes bloquean jugar");
        s2.gamePhase = "COMBAT_OPEN";
        Check(GameRules.PlayHint(s2, Unit("h3", "U", 1), new HashSet<string>()) != null, "pista: unidades no entran en combate");

        Console.WriteLine(fails == 0 ? "autopruebas de lógica: OK" : "autopruebas: " + fails + " fallo(s)");
        return fails == 0;
    }

    static async Task<Result> PlayOne(string host, string deck, Random rnd)
    {
        var res = new Result();
        string myId = Guid.NewGuid().ToString();
        var inbox = new BlockingCollection<object>();
        using (var conn = new GameConnection())
        {
            conn.StateReceived += s => inbox.Add(s);
            conn.ErrorReceived += e => inbox.Add(e);
            conn.Disconnected += d => inbox.Add(new Exception(d));
            var cts = new CancellationTokenSource();
            var pump = Task.Run(async () => { while (!cts.IsCancellationRequested) { conn.Pump(); await Task.Delay(3); } });

            await conn.ConnectAsync(host, myId);
            conn.StartLocalMatch(deck);

            GameStateDto prev = null, s = (GameStateDto)await Next(inbox);
            var pending = new HashSet<string>();
            int lastTurn = s.turn;
            var diff = StateDiff.Describe(null, s); res.DiffLines += diff.Count;
            CheckInvariants(s, res);

            for (int step = 0; step < 300; step++)
            {
                if (GameRules.IsOver(s)) { res.Outcome = "GAMEOVER"; break; }
                var me = s.activePlayer;
                if (!me.hasPriority) { res.Outcome = "STUCK"; res.Problems.Add("sin prioridad tras recibir estado"); break; }

                // --- decidir acción con las pistas del cliente
                string kind = "pass"; CardDto pick = null; Zone dest = Zone.Base;

                var castable = me.hand.Where(c => GameRules.IsSpell(c) && GameRules.PlayHint(s, c, pending) == null && GameRules.TotalTargets(c) == 0).ToList();
                var playable = me.hand.Where(c => GameRules.IsUnit(c) && GameRules.PlayHint(s, c, pending) == null).OrderByDescending(c => c.currentCost).ToList();
                var movable = GameRules.CardsIn(me, Zone.Base).Where(c => GameRules.MoveHint(s, c) == null).ToList();
                if (castable.Count > 0 && rnd.Next(3) == 0) { kind = "cast"; pick = castable[0]; }
                else if (playable.Count > 0) { kind = "play"; pick = playable[0]; }
                else if (movable.Count > 0)
                {
                    kind = "move"; pick = movable.OrderByDescending(c => c.currentDrive).First();
                    // reparte: al carril donde tengo menos unidades (así intento puntuar en los dos)
                    dest = GameRules.CardsIn(me, Zone.Left).Count <= GameRules.CardsIn(me, Zone.Right).Count ? Zone.Left : Zone.Right;
                }
                else if (castable.Count > 0) { kind = "cast"; pick = castable[0]; }

                // 1 de cada 8 pasos, intentar algo que la pista marca como ilegal para medir la fidelidad de las pistas
                bool probe = false;
                if (rnd.Next(8) == 0)
                {
                    var bad = me.hand.FirstOrDefault(c => GameRules.IsUnit(c) && GameRules.PlayHint(s, c, pending) != null && c.currentCost > me.resource)
                              ?? me.hand.FirstOrDefault(c => GameRules.IsUnit(c) && s.gamePhase != "MAIN_OPEN" && c.currentCost <= me.resource);
                    if (bad != null) { kind = "play"; pick = bad; dest = Zone.Base; probe = true; }
                    else
                    {
                        var ex = GameRules.CardsIn(me, Zone.Base).FirstOrDefault(c => c.isExhausted);
                        if (ex != null) { kind = "move"; pick = ex; dest = Zone.Left; probe = true; }
                    }
                }

                string hint = null;
                if (pick != null && (kind == "play" || kind == "cast")) hint = GameRules.PlayHint(s, pick, pending);
                if (pick != null && kind == "move") hint = GameRules.MoveHint(s, pick);

                switch (kind)
                {
                    case "play": case "cast": conn.PlayCard(pick.id, "MY_BASE", new List<string>()); break;
                    case "move": conn.MoveCard(pick.id, GameRules.ToDestination(dest)); break;
                    default: conn.PassPriority(); break;
                }
                res.Actions++;

                object msg = await Next(inbox);
                if (msg is string err)
                {
                    res.ServerErrors++;
                    if (hint == null) { res.HintTooOptimistic++; res.Problems.Add("pista optimista: " + kind + " " + (pick != null ? pick.name : "") + " -> " + err); }
                    continue;   // tras un error el servidor no manda estado nuevo
                }
                var ns = (GameStateDto)msg;
                if (hint != null) { res.HintTooStrict++; res.Problems.Add("pista estricta: " + kind + " " + pick.name + " (" + hint + ") fue aceptada por el servidor"); }

                if (kind == "move") pending.Add(pick.id);
                if (kind == "pass" || kind == "play" || kind == "cast" || ns.turn != lastTurn) pending.Clear();
                lastTurn = ns.turn;

                prev = s; s = ns;
                { var dl = StateDiff.Describe(prev, s); res.DiffLines += dl.Count; if (Verbose) { Console.WriteLine("   [" + kind + (pick != null ? " " + pick.name : "") + "] fase=" + s.gamePhase + " mano=" + string.Join(",", s.activePlayer.hand.Select(c => c.name.Substring(0, 4) + c.currentCost))); foreach (var l in dl) Console.WriteLine("      " + System.Text.RegularExpressions.Regex.Replace(l, "<[^>]+>", "")); } }
                CheckInvariants(s, res);
                if (probe && hint == null) { /* no debería ocurrir */ }
            }
            if (res.Outcome == null) res.Outcome = "STALLED";

            res.Turn = s.turn;
            res.Score = s.activePlayer.score + "-" + s.opponent.score;
            if (res.Outcome == "GAMEOVER")
            {
                if (GameRules.Patched(s))
                {
                    if (s.gameStatus != "FINISHED") res.Problems.Add("gameStatus no es FINISHED al terminar: " + s.gameStatus);
                    if (string.IsNullOrEmpty(s.winnerId)) res.Problems.Add("winnerId vacío al terminar");
                    else if ((s.winnerId == s.activePlayer.id) != (s.activePlayer.score > s.opponent.score)) res.Problems.Add("winnerId incoherente con los puntos");
                }
            }
            cts.Cancel();
            try { await pump; } catch { }
        }
        return res;
    }

    static async Task<object> Next(BlockingCollection<object> inbox)
    {
        return await Task.Run(() =>
        {
            object o;
            if (!inbox.TryTake(out o, 8000)) throw new Exception("Timeout: el servidor no respondió en 8 s.");
            if (o is Exception e) throw e;
            return o;
        });
    }

    static void CheckInvariants(GameStateDto s, Result r)
    {
        void Fail(string m) { r.Invariant++; if (r.Problems.Count < 6) r.Problems.Add("invariante: " + m); }
        var ids = new HashSet<string>();
        foreach (var p in new[] { s.activePlayer, s.opponent })
        {
            if (p.resource < 0 || p.resource > 10) Fail("recurso fuera de rango " + p.resource);
            foreach (Zone z in new[] { Zone.Base, Zone.Left, Zone.Right })
            {
                var l = GameRules.CardsIn(p, z);
                if (l.Count > 6) Fail("más de 6 unidades en " + z);
                foreach (var c in l)
                {
                    if (!ids.Add(c.id)) Fail("carta duplicada " + c.name);
                    if (c.currentDrive <= 0) Fail("unidad con Drive 0 sigue en tablero: " + c.name);
                }
            }
        }
        if (s.opponent.hand != null && s.opponent.hand.Count > 0) Fail("la mano del rival no debería viajar al cliente");
        if (s.activePlayer.hand.Count != s.activePlayer.handSize) Fail("handSize no coincide con la mano");
    }
}
