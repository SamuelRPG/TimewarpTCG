// Capa de alto nivel sobre StompClient: habla el "idioma" de tu GameController.java
//
//   Unity -> servidor (SEND):  /app/game/startLocalMatch | playCard | moveCard | passPriority
//   servidor -> Unity (MESSAGE): /topic/player/{playerId}          (GameStateDto: estado completo tras cada acción)
//                                /topic/player/{playerId}/errors   (ErrorResponseDto: texto del IllegalStateException)
using System;
using System.Collections.Generic;
using System.Threading.Tasks;

namespace TimewarpTCG
{
    public sealed class GameConnection : IDisposable
    {
        StompClient _stomp;

        public string PlayerId { get; private set; }
        public string GameId { get; private set; }
        public bool IsConnected { get { return _stomp != null && _stomp.IsConnected; } }

        public event Action<GameStateDto> StateReceived;
        public event Action<string> ErrorReceived;     // error de reglas devuelto por el servidor
        public event Action<string> Disconnected;      // conexión perdida
        public event Action<string> Log;

        /// <summary>"localhost:8080", "http://192.168.1.5:8080", "ws://host:8080/timewarp-ws"... -> URL del WebSocket.</summary>
        public static string ToWebSocketUrl(string host)
        {
            host = (host ?? "").Trim();
            if (host.Length == 0) host = "localhost:8080";
            if (host.StartsWith("ws://") || host.StartsWith("wss://")) return host.Contains("/timewarp-ws") ? host : host.TrimEnd('/') + "/timewarp-ws";
            host = host.Replace("http://", "").Replace("https://", "").TrimEnd('/');
            int slash = host.IndexOf('/');
            if (slash >= 0) host = host.Substring(0, slash);
            return "ws://" + host + "/timewarp-ws";
        }

        public async Task ConnectAsync(string host, string playerId)
        {
            PlayerId = playerId;
            GameId = null;
            _stomp = new StompClient();
            _stomp.Log += s => { var l = Log; if (l != null) l(s); };
            _stomp.Closed += r => { var d = Disconnected; if (d != null) d(r); };

            await _stomp.ConnectAsync(ToWebSocketUrl(host));
            // IMPORTANTE: suscribirse ANTES de pedir la partida, o se pierde el primer estado.
            await _stomp.SubscribeAsync("/topic/player/" + playerId, OnStateJson);
            await _stomp.SubscribeAsync("/topic/player/" + playerId + "/errors", OnErrorJson);
        }

        public void Pump() { if (_stomp != null) _stomp.Pump(); }

        // ---------------------------------------------------------------- acciones

        /// <param name="botDeck">"RANDOM" (por defecto), "AGGRO" o "CONTROL".</param>
        public void StartLocalMatch(string deckName, string botDeck = "RANDOM")
        {
            Send("startLocalMatch", Json.ToJson(new StartMatchRequest { playerId = PlayerId, deckName = deckName, botDeck = botDeck }));
        }

        public void PlayCard(string cardId, string destination, List<string> targetIds)
        {
            Send("playCard", Json.ToJson(new PlayCardRequest
            {
                gameId = GameId, playerId = PlayerId, cardId = cardId, destination = destination,
                targetIds = targetIds ?? new List<string>()
            }));
        }

        public void MoveCard(string cardId, string destination)
        {
            Send("moveCard", Json.ToJson(new MoveCardRequest { gameId = GameId, playerId = PlayerId, cardId = cardId, destination = destination }));
        }

        public void PassPriority()
        {
            Send("passPriority", Json.ToJson(new PassPriorityRequest { gameId = GameId, playerId = PlayerId }));
        }

        void Send(string action, string json)
        {
            if (_stomp == null) { RaiseDisconnected("No hay conexión."); return; }
            var t = _stomp.SendAsync("/app/game/" + action, json);
            t.ContinueWith(task =>
            {
                if (task.IsFaulted) RaiseDisconnected("No se pudo enviar '" + action + "': " + task.Exception.GetBaseException().Message);
            });
        }

        // ---------------------------------------------------------------- recepción (hilo principal, vía Pump)

        void OnStateJson(string json)
        {
            GameStateDto s;
            try { s = Json.FromJson<GameStateDto>(json); }
            catch (Exception e) { RaiseError("Respuesta del servidor ilegible: " + e.Message); return; }
            if (s == null || s.activePlayer == null) { RaiseError("Respuesta del servidor sin estado de partida."); return; }
            if (!string.IsNullOrEmpty(s.gameStateId)) GameId = s.gameStateId;
            var h = StateReceived; if (h != null) h(s);
        }

        void OnErrorJson(string json)
        {
            string msg = json;
            try { var e = Json.FromJson<ErrorDto>(json); if (e != null && !string.IsNullOrEmpty(e.message)) msg = e.message; } catch { }
            RaiseError(msg);
        }

        void RaiseError(string m) { var h = ErrorReceived; if (h != null) h(m); }
        void RaiseDisconnected(string m) { var h = Disconnected; if (h != null) h(m); }

        public void Dispose()
        {
            if (_stomp != null) { _stomp.Dispose(); _stomp = null; }
        }
    }
}
