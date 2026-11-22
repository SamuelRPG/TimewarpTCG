// Cliente STOMP 1.2 mínimo sobre WebSocket nativo (System.Net.WebSockets.ClientWebSocket).
// Tu backend usa Spring "WebSocket + STOMP" (WebSocketConfig.java), que habla este protocolo:
//   CONNECT -> CONNECTED, SUBSCRIBE /topic/..., SEND /app/..., MESSAGE (lo que empuja el servidor).
//
// Hilos: la recepción ocurre en un hilo del pool; los mensajes se encolan y se entregan al hilo
// principal cuando llamas a Pump() (en Unity: desde Update()). Así nunca tocas Unity desde otro hilo.
using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.IO;
using System.Net.WebSockets;
using System.Text;
using System.Threading;
using System.Threading.Tasks;

namespace TimewarpTCG
{
    public sealed class StompClient : IDisposable
    {
        readonly object _lock = new object();
        readonly Dictionary<string, Action<string>> _subs = new Dictionary<string, Action<string>>();
        readonly Dictionary<string, TaskCompletionSource<bool>> _receipts = new Dictionary<string, TaskCompletionSource<bool>>();
        readonly ConcurrentQueue<Action> _mainQueue = new ConcurrentQueue<Action>();
        readonly SemaphoreSlim _sendLock = new SemaphoreSlim(1, 1);

        ClientWebSocket _ws;
        CancellationTokenSource _cts;
        TaskCompletionSource<bool> _connected;
        int _nextId;
        volatile bool _closedRaised;

        public bool IsConnected { get; private set; }

        /// <summary>Mensajes de diagnóstico (se entregan en Pump(), hilo principal).</summary>
        public event Action<string> Log;
        /// <summary>La conexión se ha cerrado o ha dado error (se entrega en Pump()).</summary>
        public event Action<string> Closed;

        // ------------------------------------------------------------------ conexión

        public async Task ConnectAsync(string wsUrl, int timeoutMs = 5000)
        {
            _closedRaised = false;
            _cts = new CancellationTokenSource();
            _ws = new ClientWebSocket();
            _ws.Options.AddSubProtocol("v12.stomp");
            _connected = new TaskCompletionSource<bool>(TaskCreationOptions.RunContinuationsAsynchronously);

            var uri = new Uri(wsUrl);
            using (var cts = CancellationTokenSource.CreateLinkedTokenSource(_cts.Token))
            {
                cts.CancelAfter(timeoutMs);
                try { await _ws.ConnectAsync(uri, cts.Token); }
                catch (OperationCanceledException) { throw new Exception("Tiempo de espera agotado conectando a " + wsUrl + " (¿está arrancado el backend?)"); }
            }

            _ = Task.Run(ReceiveLoop);

            await SendRaw("CONNECT\naccept-version:1.2\nhost:" + uri.Host + "\nheart-beat:0,0\n\n\0");

            var winner = await Task.WhenAny(_connected.Task, Task.Delay(timeoutMs));
            if (winner != _connected.Task) throw new Exception("El servidor no respondió al CONNECT de STOMP.");
            await _connected.Task; // propaga error si lo hubo
            IsConnected = true;
        }

        /// <summary>Se suscribe a un destino. Spring (SimpleBroker) NO responde con RECEIPT, así que damos un margen
        /// muy corto para que el servidor registre la suscripción antes de que enviemos nada que provoque un mensaje
        /// (si no, se perdería el primer estado de la partida). Si el servidor sí envía RECEIPT, se continúa al instante.</summary>
        public async Task SubscribeAsync(string destination, Action<string> onMessage, int settleMs = 120)
        {
            string id = "sub-" + Interlocked.Increment(ref _nextId);
            string rid = "rcpt-" + id;
            var tcs = new TaskCompletionSource<bool>(TaskCreationOptions.RunContinuationsAsynchronously);
            lock (_lock) { _subs[id] = onMessage; _receipts[rid] = tcs; }

            await SendRaw("SUBSCRIBE\nid:" + id + "\ndestination:" + destination + "\nreceipt:" + rid + "\n\n\0");

            await Task.WhenAny(tcs.Task, Task.Delay(settleMs));
            lock (_lock) { _receipts.Remove(rid); }
        }

        public Task SendAsync(string destination, string jsonBody)
        {
            return SendRaw("SEND\ndestination:" + destination + "\ncontent-type:application/json\n\n" + jsonBody + "\0");
        }

        /// <summary>Entrega al hilo que la llama (el principal) todo lo recibido desde la última vez.</summary>
        public void Pump(int max = 100)
        {
            Action a;
            while (max-- > 0 && _mainQueue.TryDequeue(out a))
            {
                try { a(); }
                catch (Exception e) { Log?.Invoke("Excepción en manejador de mensaje: " + e); }
            }
        }

        public void Disconnect()
        {
            try
            {
                if (_ws != null && _ws.State == WebSocketState.Open)
                {
                    try { SendRaw("DISCONNECT\n\n\0").Wait(300); } catch { }
                    try { _ws.CloseOutputAsync(WebSocketCloseStatus.NormalClosure, "bye", CancellationToken.None).Wait(300); } catch { }
                }
            }
            finally
            {
                IsConnected = false;
                _closedRaised = true; // cierre voluntario: no avisar como error
                try { if (_cts != null) _cts.Cancel(); } catch { }
            }
        }

        public void Dispose()
        {
            Disconnect();
            try { if (_ws != null) _ws.Dispose(); } catch { }
            try { if (_cts != null) _cts.Dispose(); } catch { }
        }

        // ------------------------------------------------------------------ envío / recepción

        async Task SendRaw(string frame)
        {
            if (_ws == null || _ws.State != WebSocketState.Open) throw new Exception("No hay conexión con el servidor.");
            byte[] bytes = Encoding.UTF8.GetBytes(frame);
            await _sendLock.WaitAsync();
            try { await _ws.SendAsync(new ArraySegment<byte>(bytes), WebSocketMessageType.Text, true, _cts.Token); }
            finally { _sendLock.Release(); }
        }

        async Task ReceiveLoop()
        {
            var buf = new byte[16 * 1024];
            var ms = new MemoryStream();
            var pending = new StringBuilder();
            string reason = "El servidor cerró la conexión.";
            try
            {
                while (!_cts.IsCancellationRequested && _ws.State == WebSocketState.Open)
                {
                    ms.SetLength(0);
                    WebSocketReceiveResult r;
                    do
                    {
                        r = await _ws.ReceiveAsync(new ArraySegment<byte>(buf), _cts.Token);
                        if (r.MessageType == WebSocketMessageType.Close) goto done;
                        ms.Write(buf, 0, r.Count);
                    } while (!r.EndOfMessage);

                    pending.Append(Encoding.UTF8.GetString(ms.GetBuffer(), 0, (int)ms.Length));
                    DrainFrames(pending);
                }
            }
            catch (OperationCanceledException) { reason = null; }
            catch (Exception e) { reason = "Conexión perdida: " + e.Message; }
        done:
            IsConnected = false;
            if (reason != null && !_closedRaised)
            {
                _closedRaised = true;
                var rs = reason;
                _mainQueue.Enqueue(() => { Closed?.Invoke(rs); });
            }
        }

        // Un mensaje WebSocket puede traer 0, 1 o varios frames STOMP terminados en NUL.
        void DrainFrames(StringBuilder sb)
        {
            while (true)
            {
                int nul = -1;
                for (int i = 0; i < sb.Length; i++) if (sb[i] == '\0') { nul = i; break; }
                if (nul < 0) return;

                string frame = sb.ToString(0, nul);
                sb.Remove(0, nul + 1);
                frame = frame.TrimStart('\n', '\r');   // latidos (heart-beats) sueltos
                if (frame.Length == 0) continue;
                HandleFrame(frame);
            }
        }

        void HandleFrame(string raw)
        {
            int sep = raw.IndexOf("\n\n", StringComparison.Ordinal);
            int sepLen = 2;
            int sep2 = raw.IndexOf("\r\n\r\n", StringComparison.Ordinal);
            if (sep2 >= 0 && (sep < 0 || sep2 < sep)) { sep = sep2; sepLen = 4; }

            string head = sep >= 0 ? raw.Substring(0, sep) : raw;
            string body = sep >= 0 ? raw.Substring(sep + sepLen) : "";

            string[] lines = head.Split('\n');
            string command = lines[0].Trim();
            var headers = new Dictionary<string, string>();
            for (int i = 1; i < lines.Length; i++)
            {
                string line = lines[i].TrimEnd('\r');
                int c = line.IndexOf(':');
                if (c <= 0) continue;
                string k = line.Substring(0, c);
                if (!headers.ContainsKey(k)) headers[k] = line.Substring(c + 1);   // STOMP: vale la primera aparición
            }

            switch (command)
            {
                case "CONNECTED":
                    _connected.TrySetResult(true);
                    break;

                case "RECEIPT":
                {
                    string rid;
                    if (headers.TryGetValue("receipt-id", out rid))
                    {
                        TaskCompletionSource<bool> tcs;
                        lock (_lock) { _receipts.TryGetValue(rid, out tcs); }
                        if (tcs != null) tcs.TrySetResult(true);
                    }
                    break;
                }

                case "MESSAGE":
                {
                    string subId;
                    Action<string> handler = null;
                    if (headers.TryGetValue("subscription", out subId))
                        lock (_lock) { _subs.TryGetValue(subId, out handler); }
                    if (handler != null)
                    {
                        string b = body;
                        _mainQueue.Enqueue(() => handler(b));
                    }
                    break;
                }

                case "ERROR":
                {
                    string msg;
                    headers.TryGetValue("message", out msg);
                    string text = "Error STOMP: " + (msg ?? "") + " " + body;
                    _connected.TrySetException(new Exception(text));
                    if (!_closedRaised)
                    {
                        _closedRaised = true;
                        _mainQueue.Enqueue(() => { Closed?.Invoke(text); });
                    }
                    break;
                }
            }
        }

        void EnqueueLog(string s) { _mainQueue.Enqueue(() => { Log?.Invoke(s); }); }
    }
}
