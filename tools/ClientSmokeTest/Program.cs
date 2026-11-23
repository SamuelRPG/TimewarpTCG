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
        public string Outcome; public int StackMoveProbes, TriggersSeen, BotSteps, Turn, Actions, ServerErrors, HintTooOptimistic, HintTooStrict, DiffLines, Invariant, Casts, TargetedCasts, MaxStack;
        public string DeckInfo = "";
        public string Score; public List<string> Problems = new List<string>();
    }

    static bool Verbose;
    static string BotDeck = "RANDOM";
    static async Task<int> Main(string[] args)
    {
        Verbose = args.Length > 3 && args[3] == "v";
        string host = args.Length > 0 ? args[0] : "localhost:8080";
        string deck = args.Length > 1 ? args[1] : "AGGRO";
        int games = args.Length > 2 && int.TryParse(args[2], out var gg) ? gg : 3;
        BotDeck = args.Length > 4 ? args[4] : "RANDOM";
        int bad = 0;
        if (!SelfTests()) { Console.WriteLine("RESULTADO: FALLAN LAS AUTOPRUEBAS"); return 1; }
        var rnd = new Random(1234);
        if (deck.Equals("SECURITY", StringComparison.OrdinalIgnoreCase)) return await SecurityChecks(host);
        if (deck.Equals("PVP", StringComparison.OrdinalIgnoreCase)) return await PvpChecks(host, games);
        for (int g = 1; g <= games; g++)
        {
            Result r;
            try { r = await PlayOne(host, deck, rnd, BotDeck); }
            catch (Exception e) { r = new Result { Outcome = "EXCEPCION", Score = "-" }; r.Problems.Add(e.ToString()); }
            bool fail = r.Outcome != "GAMEOVER" && r.Outcome != "STALLED" || r.Problems.Count > 0 || r.Invariant > 0;
            if (fail) bad++;
            Console.WriteLine("partida {0,2} [{1}] {2,-9} turno={3,3} puntos={4,-6} acciones={5,3} erroresServidor={6} pistaOptimista={7} pistaEstricta={8} lineasLog={9} invariantes={10} hechizos={11}(conObjetivo={12}) pilaMax={13} pasosBot={14} efectosEnPila={15} movsRechazadosConPila={16} {17}",
                g, deck, r.Outcome, r.Turn, r.Score, r.Actions, r.ServerErrors, r.HintTooOptimistic, r.HintTooStrict, r.DiffLines, r.Invariant, r.Casts, r.TargetedCasts, r.MaxStack, r.BotSteps, r.TriggersSeen, r.StackMoveProbes, r.DeckInfo);
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

        // ---- editor de mazos (DeckDraft): las mismas reglas que valida el servidor
        {
            var cat = new List<CardDto>();
            foreach (var f in new[] { "ROMANS", "STEAMPUNK", "GLITCHPOP", "ANCIENT_EGYPT" })
                for (int i = 0; i < 9; i++)
                    cat.Add(new CardDto { id = f + "_" + i, name = "C" + i, cardType = i >= 6 ? "SPELL" : "UNIT", currentCost = 1 + i % 8, currentDrive = 2, faction = f });
            var d = new DeckDraft(cat);
            string why;
            Check(d.Validate() != null && d.Total == 0, "mazo vacío no es válido");
            var c0 = cat[0];
            Check(d.Add(c0, out why) && d.Add(c0, out why) && d.Add(c0, out why), "3 copias caben");
            Check(!d.Add(c0, out why) && why.Contains("3"), "la 4ª copia se rechaza: " + why);
            Check(d.CountOf(c0.id) == 3 && d.Total == 3, "recuento");
            // segunda facción OK, tercera rechazada
            var steam = cat.First(c => c.faction == "STEAMPUNK"); var glitch = cat.First(c => c.faction == "GLITCHPOP");
            Check(d.Add(steam, out why), "2ª facción permitida");
            Check(!d.Add(glitch, out why) && why.ToLower().Contains("facciones"), "3ª facción rechazada: " + why);
            Check(d.Factions().Count == 2, "2 facciones");
            // llenar hasta 40 con las dos facciones y comprobar tope
            foreach (var c in cat.Where(c => c.faction == "ROMANS" || c.faction == "STEAMPUNK"))
                for (int i = 0; i < 3; i++) { if (d.Total >= 40) break; d.Add(c, out why); }
            Check(d.Total == 40, "llega a 40 (tiene " + d.Total + ")");
            Check(!d.Add(cat.First(c => c.faction == "ROMANS" && d.CountOf(c.id) < 3 || c.faction == "STEAMPUNK" && d.CountOf(c.id) < 3), out why) || d.Total == 40, "no pasa de 40");
            d.Name = "Mi mazo";
            Check(d.Validate() == null, "40 cartas, 2 facciones y nombre => válido: " + d.Validate());
            d.Name = "aggro"; Check(d.Validate() != null && d.Validate().Contains("reservado"), "nombre reservado");
            d.Name = "Mi mazo";
            Check(d.ToEntries().Sum(e => e.quantity) == 40 && d.ToEntries().All(e => e.quantity >= 1 && e.quantity <= 3), "ToEntries suma 40 y respeta 1..3");
            Check(d.CostCurve().Sum() == 40, "la curva suma 40");
            Check(d.Units() + d.Spells() == 40, "unidades + hechizos = 40");
            d.Remove(d.Cards()[0].Key.id);
            Check(d.Total == 39 && d.Validate().Contains("Faltan 1"), "quitar una => faltan 1: " + d.Validate());
            // cargar un mazo guardado (con una carta que ya no existe en el catálogo)
            var saved = new DeckDto { name = "Guardado", total = 40, factions = new List<string> { "ROMANS" },
                cards = new List<DeckEntryDto> { new DeckEntryDto { cardId = "ROMANS_0", quantity = 3 }, new DeckEntryDto { cardId = "NO_EXISTE", quantity = 2 } } };
            var d2 = new DeckDraft(cat);
            Check(d2.Load(saved) == 2 && d2.Total == 3 && d2.SavedAs == "Guardado" && d2.Name == "Guardado", "Load omite cartas inexistentes");
        }

        // el cifrado nunca se degrada: https:// => wss://, y http:// o sin esquema => ws://
        Check(GameConnection.ToWebSocketUrl("https://juego.example.com") == "wss://juego.example.com/timewarp-ws", "https:// debe dar wss://");
        Check(GameConnection.ToWebSocketUrl("https://juego.example.com/").StartsWith("wss://"), "https:// con barra final debe dar wss://");
        Check(GameConnection.ToWebSocketUrl("wss://juego.example.com") == "wss://juego.example.com/timewarp-ws", "wss:// se respeta");
        Check(GameConnection.ToWebSocketUrl("http://localhost:8080") == "ws://localhost:8080/timewarp-ws", "http:// da ws://");
        Check(GameConnection.ToWebSocketUrl("localhost:8080") == "ws://localhost:8080/timewarp-ws", "sin esquema da ws://");
        Check(RestApi.ToHttpBase("wss://juego.example.com").StartsWith("https://"), "wss:// debe dar https:// en REST");

        Console.WriteLine(fails == 0 ? "autopruebas de lógica: OK" : "autopruebas: " + fails + " fallo(s)");
        return fails == 0;
    }

    static async Task<Result> PlayOne(string host, string deck, Random rnd, string botDeck)
    {
        var res = new Result();
        string myId, myToken;
        string deckName = deck;
        if (deck.Equals("CUSTOM", StringComparison.OrdinalIgnoreCase))
        {
            // Flujo real del editor de mazos: crear cuenta (con contraseña), leer catálogo, guardar un mazo legal, jugar con él
            var made = await MakeCustomDeck(host, rnd, res);
            myId = made.Item1; deckName = made.Item2; myToken = made.Item3;
        }
        else
        {
            var guest = await RestApi.GuestAsync(host);     // invitado: identidad firmada por el servidor
            myId = guest.id; myToken = guest.token;
        }
        var inbox = new BlockingCollection<object>();
        using (var conn = new GameConnection())
        {
            conn.StateReceived += s => inbox.Add(s);
            conn.ErrorReceived += e => inbox.Add(e);
            conn.Disconnected += d => inbox.Add(new Exception(d));
            var cts = new CancellationTokenSource();
            var pump = Task.Run(async () => { while (!cts.IsCancellationRequested) { conn.Pump(); await Task.Delay(3); } });

            await conn.ConnectAsync(host, myId, myToken);
            conn.StartLocalMatch(deckName, botDeck);

            GameStateDto prev = null, s = (GameStateDto)await Next(inbox);
            var pending = new HashSet<string>();
            int lastTurn = s.turn;
            var diff = StateDiff.Describe(null, s); res.DiffLines += diff.Count;
            CheckInvariants(s, res);

            for (int step = 0; step < 300; step++)
            {
                if (GameRules.IsOver(s)) { res.Outcome = "GAMEOVER"; break; }
                // El bot juega PASO A PASO (un estado por accion, con pausa): hasta que devuelva la prioridad llegan varios estados seguidos
                for (int waits = 0; !GameRules.IsOver(s) && !s.activePlayer.hasPriority && waits < 600; waits++)
                {
                    object nm = await Next(inbox);
                    if (nm is string) continue;                       // un aviso de error del servidor: sigo esperando el estado
                    var gs = (GameStateDto)nm;
                    if (gs.turn != lastTurn) pending.Clear();
                    lastTurn = gs.turn;
                    prev = s; s = gs;
                    res.BotSteps++;
                    var dl2 = StateDiff.Describe(prev, s); res.DiffLines += dl2.Count;
                    if (s.spellStack != null && s.spellStack.Count > res.MaxStack) res.MaxStack = s.spellStack.Count;
                    CheckInvariants(s, res);
                }
                var me = s.activePlayer;
                if (GameRules.IsOver(s)) { res.Outcome = "GAMEOVER"; break; }
                if (!me.hasPriority) { res.Outcome = "STUCK"; res.Problems.Add("sin prioridad tras recibir estado"); break; }

                // --- decidir acción con las pistas del cliente
                string kind = "pass"; CardDto pick = null; Zone dest = Zone.Base;

                List<string> targets = new List<string>();
                var castable = me.hand.Where(c => GameRules.IsSpell(c) && GameRules.PlayHint(s, c, pending) == null && Satisfiable(s, c)).ToList();
                var playable = me.hand.Where(c => GameRules.IsUnit(c) && GameRules.PlayHint(s, c, pending) == null).OrderByDescending(c => c.currentCost).ToList();
                var movable = GameRules.CardsIn(me, Zone.Base).Where(c => GameRules.MoveHint(s, c) == null).ToList();
                if (castable.Count > 0 && rnd.Next(2) == 0) { kind = "cast"; pick = castable[rnd.Next(castable.Count)]; }
                else if (playable.Count > 0) { kind = "play"; pick = playable[0]; }
                else if (movable.Count > 0)
                {
                    kind = "move"; pick = movable.OrderByDescending(c => c.currentDrive).First();
                    // reparte: al carril donde tengo menos unidades (así intento puntuar en los dos)
                    dest = GameRules.CardsIn(me, Zone.Left).Count <= GameRules.CardsIn(me, Zone.Right).Count ? Zone.Left : Zone.Right;
                }
                else if (castable.Count > 0) { kind = "cast"; pick = castable[0]; }
                if (pick != null && (kind == "cast" || kind == "play")) targets = PickTargets(s, pick);

                // 1 de cada 8 pasos, intentar algo que la pista marca como ilegal para medir la fidelidad de las pistas
                bool probe = false;
                // con la PILA ocupada mover esta prohibido (solo responder con FAST/INSTANT o pasar): se prueba siempre que ocurra
                var readyOnBase = GameRules.StackClosesPhase(s) ? GameRules.CardsIn(me, Zone.Base).FirstOrDefault(c => !c.isExhausted) : null;
                if (readyOnBase != null && rnd.Next(2) == 0)
                {
                    kind = "move"; pick = readyOnBase; dest = Zone.Left; probe = true; res.StackMoveProbes++;
                }
                else if (rnd.Next(8) == 0)
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

                await Task.Delay(55);   // ritmo de un jugador muy rapido (~18 acciones/s): el servidor limita las rafagas
                switch (kind)
                {
                    case "play": case "cast":
                        conn.PlayCard(pick.id, "MY_BASE", probe ? new List<string>() : targets);
                        if (kind == "cast" && !probe) { res.Casts++; if (targets.Count > 0) res.TargetedCasts++; }
                        break;
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
                if (s.spellStack != null && s.spellStack.Count > res.MaxStack) res.MaxStack = s.spellStack.Count;
                if (s.spellStack != null) foreach (var se in s.spellStack) if (se.kind == "TRIGGER") { res.TriggersSeen++; break; }
                { var dl = StateDiff.Describe(prev, s); res.DiffLines += dl.Count; if (Verbose) { Console.WriteLine("   [" + kind + (pick != null ? " " + pick.name : "") + "] fase=" + s.gamePhase + " mano=" + string.Join(",", s.activePlayer.hand.Select(c => (c.cardType == "SPELL" ? "*" : "") + c.name.Substring(0, 4) + c.currentCost + (c.spellSpeed ?? "").Substring(0, 1)))); foreach (var l in dl) Console.WriteLine("      " + System.Text.RegularExpressions.Regex.Replace(l, "<[^>]+>", "")); } }
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

    // Elige objetivos como lo haría un jugador: el enemigo/aliado de más Drive para cada requisito.
    static List<string> PickTargets(GameStateDto s, CardDto c)
    {
        var chosen = new List<string>();
        foreach (var req in GameRules.Requirements(c))
        {
            var cands = new List<CardDto>();
            foreach (var x in GameRules.Board(s.activePlayer)) if (GameRules.IsValidTarget(req.group, true)) cands.Add(x);
            foreach (var x in GameRules.Board(s.opponent)) if (GameRules.IsValidTarget(req.group, false)) cands.Add(x);
            cands = cands.Where(x => !chosen.Contains(x.id)).OrderByDescending(x => x.currentDrive).ToList();
            for (int i = 0; i < req.count && i < cands.Count; i++) chosen.Add(cands[i].id);
        }
        return chosen;
    }

    static bool Satisfiable(GameStateDto s, CardDto c) { return PickTargets(s, c).Count == GameRules.TotalTargets(c); }

    static async Task<Tuple<string, string, string>> MakeCustomDeck(string host, Random rnd, Result res)
    {
        var user = await RestApi.RegisterAsync(host, "smoke_" + Guid.NewGuid().ToString("N").Substring(0, 8), "contraseña-de-prueba-" + rnd.Next(100000));
        RestApi.Token = user.token;
        var cards = await RestApi.GetCardsAsync(host);
        var factions = cards.Select(c => c.faction).Distinct().OrderBy(x => rnd.Next()).Take(2).ToList();
        var pool = cards.Where(c => factions.Contains(c.faction)).ToList();
        var counts = new Dictionary<string, int>();
        int total = 0;
        void Fill(List<CardDto> src, int target)
        {
            bool progress = true;
            while (total < target && progress)
            {
                progress = false;
                foreach (var c in src.OrderBy(x => rnd.Next()))
                {
                    int have = counts.ContainsKey(c.id) ? counts[c.id] : 0;
                    int add = Math.Min(Math.Min(3 - have, target - total), 1 + rnd.Next(3));
                    if (add <= 0) continue;
                    counts[c.id] = have + add; total += add; progress = true;
                    if (total >= target) break;
                }
            }
        }
        int spells = 8 + rnd.Next(5);
        Fill(pool.Where(c => c.cardType == "UNIT").ToList(), 40 - spells);
        Fill(pool.Where(c => c.cardType == "SPELL").ToList(), 40);
        Fill(pool, 40);
        var entries = counts.Select(kv => new DeckEntryDto { cardId = kv.Key, quantity = kv.Value }).ToList();
        if (entries.Sum(e => e.quantity) != 40) throw new Exception("el agente no pudo montar un mazo de 40");
        string name = "Smoke " + string.Join("+", factions.Select(f => f.Substring(0, 3)));
        var saved = await RestApi.SaveDeckAsync(host, name, entries);
        var listed = await RestApi.GetDecksAsync(host);
        if (saved.total != 40 || !listed.Any(d => d.name == name && d.total == 40)) throw new Exception("el mazo guardado no aparece en la lista");
        res.DeckInfo = "[mazo " + name + ": " + pool.Where(c => c.cardType == "SPELL" && counts.ContainsKey(c.id)).Sum(c => counts[c.id]) + " hechizos de 40]";
        return Tuple.Create(user.id, name, user.token);
    }

    // ------------------------------------------------------------------ ataques de prueba contra el servidor real
    static async Task<int> SecurityChecks(string host)
    {
        int fails = 0;
        void Check(bool ok, string what) { Console.WriteLine((ok ? "  [OK]    " : "  [FALLO] ") + what); if (!ok) fails++; }
        string http = RestApi.ToHttpBase(host);
        var hc = new System.Net.Http.HttpClient { Timeout = TimeSpan.FromSeconds(8) };
        async Task<int> Status(System.Net.Http.HttpMethod m, string path, string token, string json = null)
        {
            var req = new System.Net.Http.HttpRequestMessage(m, http + path);
            if (token != null) req.Headers.TryAddWithoutValidation("Authorization", "Bearer " + token);
            if (json != null) req.Content = new System.Net.Http.StringContent(json, System.Text.Encoding.UTF8, "application/json");
            return (int)(await hc.SendAsync(req)).StatusCode;
        }
        var get = System.Net.Http.HttpMethod.Get; var post = System.Net.Http.HttpMethod.Post; var del = System.Net.Http.HttpMethod.Delete;
        var stompField = typeof(GameConnection).GetField("_stomp", System.Reflection.BindingFlags.NonPublic | System.Reflection.BindingFlags.Instance);

        Console.WriteLine("REST");
        Check(await Status(get, "/api/menu/decks", null) == 401, "mazos sin token -> 401");
        Check(await Status(get, "/api/menu/decks?userId=" + Guid.NewGuid(), null) == 401, "formato antiguo (userId en la URL) sin token -> 401");
        Check(await Status(get, "/api/menu/decks", "token.falso.aqui") == 401, "token falso -> 401");
        Check(await Status(del, "/api/users/" + Guid.NewGuid(), null) != 200, "DELETE /api/users/{id} (antiguo, sin autenticar) ya no borra cuentas");
        Check(await Status(post, "/api/users/login?username=x&password=y", null, "{}") != 200, "login por parametros de URL ya no existe");

        var a = await RestApi.RegisterAsync(host, "sec_a_" + Guid.NewGuid().ToString("N").Substring(0, 8), "contraseña-segura-A1");
        var b = await RestApi.RegisterAsync(host, "sec_b_" + Guid.NewGuid().ToString("N").Substring(0, 8), "contraseña-segura-B2");
        var g = await RestApi.GuestAsync(host);
        Check(await Status(get, "/api/menu/decks", a.token) == 200, "mazos con token propio -> 200");

        Console.WriteLine("WEBSOCKET");
        try { using (var c = new GameConnection()) { await c.ConnectAsync(host, a.id, ""); Check(false, "CONNECT sin token debe fallar"); } }
        catch (Exception) { Check(true, "CONNECT sin token -> rechazado"); }
        try { using (var c = new GameConnection()) { await c.ConnectAsync(host, a.id, "eyJhbGciOiJIUzI1NiJ9.e30.firmafalsa"); Check(false, "CONNECT con token falso debe fallar"); } }
        catch (Exception) { Check(true, "CONNECT con token falso -> rechazado"); }

        // espiar el canal de otro jugador: la suscripcion se rechaza y el servidor cierra la sesion
        {
            var closed = new TaskCompletionSource<string>();
            var stomp = new StompClient();
            stomp.Closed += r => closed.TrySetResult(r);
            await stomp.ConnectAsync(GameConnection.ToWebSocketUrl(host), a.token);
            var pump = Task.Run(async () => { while (!closed.Task.IsCompleted) { stomp.Pump(); await Task.Delay(5); } });
            try { await stomp.SubscribeAsync("/topic/player/" + b.id, s => { }, 100); } catch (Exception) { }
            var done = await Task.WhenAny(closed.Task, Task.Delay(3000));
            Check(done == closed.Task, "suscribirse al canal de OTRO jugador -> el servidor corta la sesion");
            stomp.Dispose();
        }

        // suplantar: A manda startLocalMatch diciendo ser B
        using (var ca = new GameConnection())
        {
            var gotState = new TaskCompletionSource<bool>(); var gotError = new TaskCompletionSource<string>();
            ca.StateReceived += st => gotState.TrySetResult(true);
            ca.ErrorReceived += e => gotError.TrySetResult(e);
            await ca.ConnectAsync(host, a.id, a.token);
            var pump = Task.Run(async () => { for (int i = 0; i < 600; i++) { ca.Pump(); await Task.Delay(5); } });
            var stompA = (StompClient)stompField.GetValue(ca);
            await stompA.SendAsync("/app/game/startLocalMatch", Json.ToJson(new StartMatchRequest { playerId = b.id, deckName = "AGGRO", botDeck = "AGGRO" }));
            var first = await Task.WhenAny(gotError.Task, gotState.Task, Task.Delay(3000));
            Check(first == gotError.Task && gotError.Task.Result.Contains("no coincide"), "startLocalMatch con el playerId de OTRO -> error 'no coincide' (" + (first == gotError.Task ? gotError.Task.Result : "sin error") + ")");
            Check(!gotState.Task.IsCompleted, "y no se crea ninguna partida a nombre de otro");
        }

        // suplantar en la cola online: A intenta ponerse en cola con el id de B
        using (var ca = new GameConnection())
        {
            var gotStatus = new TaskCompletionSource<bool>(); var gotError = new TaskCompletionSource<string>();
            ca.StatusReceived += st => gotStatus.TrySetResult(true);
            ca.ErrorReceived += e => gotError.TrySetResult(e);
            await ca.ConnectAsync(host, a.id, a.token);
            var pump = Task.Run(async () => { for (int i = 0; i < 500; i++) { ca.Pump(); await Task.Delay(5); } });
            var stompA = (StompClient)stompField.GetValue(ca);
            await stompA.SendAsync("/app/game/joinQueue", Json.ToJson(new JoinQueueRequest { playerId = b.id, deckName = "AGGRO" }));
            var first = await Task.WhenAny(gotError.Task, gotStatus.Task, Task.Delay(3000));
            Check(first == gotError.Task && gotError.Task.Result.Contains("no coincide"), "joinQueue con el playerId de OTRO -> error 'no coincide'");
            Check(!gotStatus.Task.IsCompleted, "y nadie entra en la cola a nombre de otro");
        }

        // B juega una partida; A intenta pasar prioridad en la partida de B
        using (var cb = new GameConnection())
        using (var ca = new GameConnection())
        {
            var bState = new TaskCompletionSource<GameStateDto>(); int bStates = 0; GameStateDto bLast = null;
            cb.StateReceived += st => { bStates++; bLast = st; bState.TrySetResult(st); };
            var aError = new TaskCompletionSource<string>(); var aState = new TaskCompletionSource<bool>();
            ca.ErrorReceived += e => aError.TrySetResult(e);
            ca.StateReceived += st => aState.TrySetResult(true);
            await cb.ConnectAsync(host, b.id, b.token);
            await ca.ConnectAsync(host, a.id, a.token);
            var pump = Task.Run(async () => { for (int i = 0; i < 2400; i++) { cb.Pump(); ca.Pump(); await Task.Delay(5); } });
            cb.StartLocalMatch("AGGRO", "AGGRO");
            var st0 = await Task.WhenAny(bState.Task, Task.Delay(4000)) == bState.Task ? bState.Task.Result : null;
            Check(st0 != null, "B inicia su partida");
            if (st0 != null)
            {
                // el bot de B juega paso a paso y emite estados por su cuenta: esperamos a que B tenga la prioridad (el bot quieto)
                for (int w = 0; w < 200 && (bLast == null || !bLast.activePlayer.hasPriority); w++) await Task.Delay(50);
                Check(bLast != null && bLast.activePlayer.hasPriority, "B recupera la prioridad (el bot deja de jugar)");
                int before = bStates;
                var stompA = (StompClient)stompField.GetValue(ca);
                await stompA.SendAsync("/app/game/passPriority", Json.ToJson(new PassPriorityRequest { gameId = st0.gameStateId, playerId = a.id }));
                var r = await Task.WhenAny(aError.Task, Task.Delay(3000));
                Check(r == aError.Task, "A actuando en la partida de B -> error (" + (r == aError.Task ? aError.Task.Result : "sin respuesta") + ")");
                await Task.Delay(300);
                Check(bStates == before, "la partida de B no cambia");
                Check(!aState.Task.IsCompleted, "A no recibe el estado de la partida de B");
            }
        }

        // invitado: solo mazos de prueba
        using (var cg = new GameConnection())
        {
            var err = new TaskCompletionSource<string>(); var st = new TaskCompletionSource<bool>();
            cg.ErrorReceived += e => err.TrySetResult(e); cg.StateReceived += s2 => st.TrySetResult(true);
            await cg.ConnectAsync(host, g.id, g.token);
            var pump = Task.Run(async () => { for (int i = 0; i < 400; i++) { cg.Pump(); await Task.Delay(5); } });
            cg.StartLocalMatch("MiMazoInventado", "AGGRO");
            var r = await Task.WhenAny(err.Task, st.Task, Task.Delay(3000));
            Check(r == err.Task, "invitado con un mazo propio -> rechazado");
        }

        // inundacion de mensajes: el servidor corta la sesion
        {
            var closed = new TaskCompletionSource<string>();
            var stomp = new StompClient();
            stomp.Closed += r => closed.TrySetResult(r);
            await stomp.ConnectAsync(GameConnection.ToWebSocketUrl(host), a.token);
            var pump = Task.Run(async () => { while (!closed.Task.IsCompleted) { stomp.Pump(); await Task.Delay(5); } });
            try { for (int i = 0; i < 400; i++) await stomp.SendAsync("/app/game/passPriority", "{}"); } catch (Exception) { }
            var done = await Task.WhenAny(closed.Task, Task.Delay(4000));
            Check(done == closed.Task, "inundar con 400 mensajes en un instante -> sesion cortada");
            stomp.Dispose();
        }

        Console.WriteLine(fails == 0 ? "SEGURIDAD: TODO OK" : "SEGURIDAD: " + fails + " comprobacion(es) FALLAN");
        return fails == 0 ? 0 : 1;
    }

    // ------------------------------------------------------------------ partida ONLINE entre dos personas (dos clientes reales)
    sealed class PvpAgent
    {
        public string Name, Id;
        public GameConnection Conn;
        public readonly BlockingCollection<GameStateDto> States = new BlockingCollection<GameStateDto>();
        public readonly BlockingCollection<MatchStatusDto> Statuses = new BlockingCollection<MatchStatusDto>();
        public readonly BlockingCollection<string> Errors = new BlockingCollection<string>();
        public readonly List<string> ErrorLog = new List<string>();
        public bool Dropped;
        CancellationTokenSource _cts = new CancellationTokenSource();

        public static async Task<PvpAgent> Create(string host)
        {
            var g = await RestApi.GuestAsync(host);
            var a = new PvpAgent { Name = g.username, Id = g.id, Conn = new GameConnection() };
            a.Conn.StateReceived += s => a.States.Add(s);
            a.Conn.StatusReceived += st => a.Statuses.Add(st);
            a.Conn.ErrorReceived += e => { a.ErrorLog.Add(e); a.Errors.Add(e); };
            a.Conn.Disconnected += d => a.Dropped = true;
            await a.Conn.ConnectAsync(host, g.id, g.token);
            Task.Run(async () => { while (!a._cts.IsCancellationRequested) { try { a.Conn.Pump(); } catch { } await Task.Delay(3); } });
            return a;
        }
        public async Task<MatchStatusDto> Status(string type, int ms = 6000)
        {
            return await Task.Run(() =>
            {
                var end = DateTime.UtcNow.AddMilliseconds(ms);
                MatchStatusDto st;
                while (DateTime.UtcNow < end) { if (Statuses.TryTake(out st, 200) && st.type == type) return st; }
                return null;
            });
        }
        public async Task<GameStateDto> State(int ms = 8000)
        {
            return await Task.Run(() => { GameStateDto s; return States.TryTake(out s, ms) ? s : null; });
        }
        public void Close() { _cts.Cancel(); Conn.Dispose(); }
    }

    /// <summary>Juega una partida como un jugador rapido hasta que acabe. Devuelve el ultimo estado visto.</summary>
    static async Task<GameStateDto> PvpPlay(PvpAgent me, GameStateDto s, Random rnd, int maxSteps = 700)
    {
        var pending = new HashSet<string>();
        int lastTurn = s.turn, failsInARow = 0;
        for (int step = 0; step < maxSteps; step++)
        {
            if (GameRules.IsOver(s)) return s;
            if (!s.activePlayer.hasPriority)
            {
                var ns = await me.State(15000);
                if (ns == null) throw new Exception(me.Name + ": no llego ningun estado en 15 s (la partida se ha quedado parada)");
                if (ns.turn != lastTurn) { pending.Clear(); lastTurn = ns.turn; }
                s = ns; continue;
            }
            var mine = s.activePlayer;
            string kind = "pass"; CardDto pick = null; Zone dest = Zone.Base; var targets = new List<string>();
            var castable = mine.hand.Where(c => GameRules.IsSpell(c) && GameRules.PlayHint(s, c, pending) == null && Satisfiable(s, c)).ToList();
            var playable = mine.hand.Where(c => GameRules.IsUnit(c) && GameRules.PlayHint(s, c, pending) == null).OrderByDescending(c => c.currentCost).ToList();
            var movable = GameRules.CardsIn(mine, Zone.Base).Where(c => GameRules.MoveHint(s, c) == null).ToList();
            if (failsInARow == 0)
            {
                if (castable.Count > 0 && rnd.Next(3) == 0) { kind = "cast"; pick = castable[rnd.Next(castable.Count)]; }
                else if (playable.Count > 0) { kind = "play"; pick = playable[0]; }
                else if (movable.Count > 0)
                {
                    kind = "move"; pick = movable.OrderByDescending(c => c.currentDrive).First();
                    dest = GameRules.CardsIn(mine, Zone.Left).Count <= GameRules.CardsIn(mine, Zone.Right).Count ? Zone.Left : Zone.Right;
                }
                if (pick != null && (kind == "cast" || kind == "play")) targets = PickTargets(s, pick);
            }
            await Task.Delay(55);
            switch (kind)
            {
                case "cast": case "play": me.Conn.PlayCard(pick.id, "MY_BASE", targets); break;
                case "move": me.Conn.MoveCard(pick.id, GameRules.ToDestination(dest)); break;
                default: me.Conn.PassPriority(); break;
            }
            // espero el estado resultante o un error
            GameStateDto got = null; string err = null;
            await Task.Run(() =>
            {
                var end = DateTime.UtcNow.AddSeconds(15);
                while (DateTime.UtcNow < end && got == null && err == null)
                {
                    if (me.States.TryTake(out got, 50)) break;
                    if (me.Errors.TryTake(out err, 0)) break;
                }
            });
            if (err != null) { failsInARow++; if (failsInARow > 3) throw new Exception(me.Name + ": el servidor rechaza tambien pasar prioridad: " + err); continue; }
            if (got == null) throw new Exception(me.Name + ": el servidor no respondio a '" + kind + "' en 15 s");
            failsInARow = 0;
            if (kind == "move") pending.Add(pick.id);
            if (kind == "pass" || kind == "play" || kind == "cast" || got.turn != lastTurn) pending.Clear();
            lastTurn = got.turn; s = got;
        }
        return s;
    }

    static async Task<int> PvpChecks(string host, int games)
    {
        int fails = 0;
        void Check(bool ok, string what) { Console.WriteLine((ok ? "  [OK]    " : "  [FALLO] ") + what); if (!ok) fails++; }
        var rnd = new Random();

        async Task<Tuple<PvpAgent, PvpAgent, GameStateDto, GameStateDto>> Pair(string deckA, string deckB)
        {
            var a = await PvpAgent.Create(host); var b = await PvpAgent.Create(host);
            a.Conn.JoinQueue(deckA);
            if (await a.Status("QUEUED") == null) throw new Exception("A no recibio QUEUED");
            b.Conn.JoinQueue(deckB);
            if (await a.Status("MATCHED") == null || await b.Status("MATCHED") == null) throw new Exception("no hubo MATCHED para los dos");
            var sa = await a.State(); var sb = await b.State();
            if (sa == null || sb == null) throw new Exception("no llego el estado inicial a los dos");
            return Tuple.Create(a, b, sa, sb);
        }

        for (int g = 1; g <= games; g++)
        {
            Console.WriteLine("PARTIDA ONLINE " + g);
            var p = await Pair("AGGRO", "CONTROL");
            PvpAgent A = p.Item1, B = p.Item2; GameStateDto sa = p.Item3, sb = p.Item4;
            Check(!string.IsNullOrEmpty(sa.gameStateId) && sa.gameStateId == sb.gameStateId, "los dos juegan la MISMA partida");
            Check(sa.activePlayer.id == A.Id && sb.activePlayer.id == B.Id, "cada uno se ve a si mismo como 'activePlayer' (su perspectiva)");
            Check(sa.activePlayer.id != sb.activePlayer.id, "y son personas distintas");
            Check(sa.activePlayer.hand != null && sa.activePlayer.hand.Count == 4 && sb.activePlayer.hand != null && sb.activePlayer.hand.Count == 4, "cada uno ve su mano de 4 cartas");
            Check(sa.opponent.hand == null && sb.opponent.hand == null, "la mano del rival NO viaja (informacion oculta)");
            Check(sa.activePlayer.hasPriority != sb.activePlayer.hasPriority, "solo uno empieza con la prioridad");

            // el que NO tiene prioridad intenta actuar: el servidor debe rechazarlo
            var waiting = sa.activePlayer.hasPriority ? B : A;
            waiting.Conn.PassPriority();
            await Task.Delay(700);
            Check(waiting.Errors.Count > 0, "actuar sin tener la prioridad -> error del servidor (" + (waiting.ErrorLog.Count > 0 ? waiting.ErrorLog[0] : "sin error") + ")");
            string e; while (waiting.Errors.TryTake(out e)) { }

            var ta = PvpPlay(A, sa, rnd); var tb = PvpPlay(B, sb, rnd);
            GameStateDto fa = null, fb = null; string crash = null;
            try
            {
                var done = Task.WhenAll(ta, tb);
                if (await Task.WhenAny(done, Task.Delay(TimeSpan.FromSeconds(150))) != done) crash = "la partida no termino en 150 s";
                else { fa = ta.Result; fb = tb.Result; }
            }
            catch (Exception ex) { crash = ex.GetBaseException().Message; }
            Check(crash == null, "la partida entre dos personas llega al final" + (crash != null ? " (" + crash + ")" : ""));
            if (crash == null)
            {
                Check(GameRules.IsOver(fa) && GameRules.IsOver(fb), "los dos ven el final de la partida");
                Check(fa.winnerId == fb.winnerId && !string.IsNullOrEmpty(fa.winnerId), "los dos coinciden en el ganador");
                Check(fa.activePlayer.score == fb.opponent.score && fb.activePlayer.score == fa.opponent.score, "los marcadores coinciden vistos desde cada lado (" + fa.activePlayer.score + "-" + fa.opponent.score + ")");
                Check(GameRules.IWon(fa) == (fa.winnerId == A.Id) && GameRules.IWon(fb) == (fb.winnerId == B.Id), "'he ganado' es coherente para cada uno");
            }
            A.Close(); B.Close();
        }

        Console.WriteLine("ABANDONAR");
        {
            var p = await Pair("AGGRO", "AGGRO"); PvpAgent A = p.Item1, B = p.Item2;
            A.Conn.Concede();
            var fa = await A.State(5000); var fb = await B.State(5000);
            Check(fa != null && fb != null && GameRules.IsOver(fa) && GameRules.IsOver(fb), "al abandonar, la partida termina para los dos");
            Check(fb != null && fb.winnerId == B.Id && GameRules.IWon(fb), "gana quien NO abandono");
            A.Close(); B.Close();
        }

        Console.WriteLine("EMPEZAR OTRA BUSQUEDA CON UNA PARTIDA EN CURSO");
        {
            var p = await Pair("AGGRO", "AGGRO"); PvpAgent A = p.Item1, B = p.Item2;
            A.Conn.JoinQueue("AGGRO");                        // A se pone a buscar otra partida sin haber acabado esta
            var fb = await B.State(6000);
            Check(fb != null && GameRules.IsOver(fb) && fb.winnerId == B.Id, "el rival se entera y gana (no se queda colgado esperando)");
            A.Close(); B.Close();
        }

        Console.WriteLine("DESCONEXION BRUSCA");
        {
            var p = await Pair("CONTROL", "AGGRO"); PvpAgent A = p.Item1, B = p.Item2;
            A.Close();                                        // cierra la conexion sin avisar (ventana cerrada, red caida...)
            var fb = await B.State(8000);
            Check(fb != null && GameRules.IsOver(fb) && fb.winnerId == B.Id, "si el rival se desconecta, ganas por abandono (" + (fb == null ? "no llego nada" : "winner=" + (fb.winnerId == B.Id ? "yo" : "otro")) + ")");
            B.Close();
        }

        Console.WriteLine("COLA");
        {
            var c = await PvpAgent.Create(host);
            c.Conn.JoinQueue("AGGRO");
            Check(await c.Status("QUEUED") != null, "entrar en la cola avisa QUEUED");
            c.Conn.LeaveQueue();
            Check(await c.Status("LEFT") != null, "salir de la cola avisa LEFT");
            var d = await PvpAgent.Create(host);
            d.Conn.JoinQueue("AGGRO");
            Check(await d.Status("QUEUED") != null && await d.Status("MATCHED", 1500) == null, "quien salio de la cola ya no se empareja con nadie");
            var guest = await PvpAgent.Create(host);
            guest.Conn.JoinQueue("MiMazoInventado");
            await Task.Delay(700);
            Check(guest.Errors.Count > 0, "un invitado con un mazo propio no entra en la cola (" + (guest.ErrorLog.Count > 0 ? guest.ErrorLog[0] : "sin error") + ")");
            c.Close(); d.Close(); guest.Close();               // al cerrarse sus conexiones salen de la cola
            await Task.Delay(800);
            var self = await PvpAgent.Create(host);
            self.Conn.JoinQueue("AGGRO"); self.Conn.JoinQueue("AGGRO");
            Check(await self.Status("QUEUED") != null && await self.Status("MATCHED", 1200) == null, "pulsar 'buscar' dos veces no te empareja contigo mismo");
            self.Close();
        }

        Console.WriteLine(fails == 0 ? "ONLINE: TODO OK" : "ONLINE: " + fails + " comprobacion(es) FALLAN");
        return fails == 0 ? 0 : 1;
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
