// Llamadas REST opcionales (UserController.java): login / registro por nombre de usuario.
// Para tu primera partida NO hacen falta: el modo "invitado" genera un id aleatorio y el backend
// acepta cualquier UUID para los mazos de prueba AGGRO / CONTROL.
using System;
using System.Collections.Generic;
using System.Net.Http;
using System.Text;
using System.Threading.Tasks;

namespace TimewarpTCG
{
    public static class RestApi
    {
        static readonly HttpClient Http = new HttpClient { Timeout = TimeSpan.FromSeconds(6) };

        public static string ToHttpBase(string host)
        {
            host = (host ?? "").Trim();
            if (host.Length == 0) host = "localhost:8080";
            host = host.Replace("ws://", "http://").Replace("wss://", "https://");
            if (!host.StartsWith("http://") && !host.StartsWith("https://")) host = "http://" + host;
            host = host.TrimEnd('/');
            int i = host.IndexOf("/timewarp-ws", StringComparison.Ordinal);
            if (i >= 0) host = host.Substring(0, i);
            return host;
        }

        /// <summary>Token de la sesión actual (lo fija quien inicia sesión). Se envía como "Authorization: Bearer ..." en las llamadas protegidas.</summary>
        public static string Token;

        // ---------------------------------------------------------------- cuentas
        // El servidor identifica al jugador SOLO por el token firmado que devuelve esto. La contraseña va en el cuerpo (nunca en la URL).

        public static Task<UserDto> RegisterAsync(string host, string username, string password) { return AuthCall(host, "/api/users/register", username, password); }
        public static Task<UserDto> LoginAsync(string host, string username, string password) { return AuthCall(host, "/api/users/login", username, password); }

        /// <summary>Identidad temporal de invitado (sin cuenta): solo permite jugar con los mazos AGGRO / CONTROL.</summary>
        public static Task<UserDto> GuestAsync(string host) { return AuthCall(host, "/api/users/guest", null, null); }

        static async Task<UserDto> AuthCall(string host, string path, string username, string password)
        {
            string json = path.EndsWith("/guest") ? "{}" : Json.ToJson(new AuthRequest { username = username, password = password });
            var resp = await Http.PostAsync(ToHttpBase(host) + path, new StringContent(json, Encoding.UTF8, "application/json"));
            string body = await resp.Content.ReadAsStringAsync();
            if (!resp.IsSuccessStatusCode) throw new Exception(ErrorText(body, (int)resp.StatusCode));
            var user = Json.FromJson<UserDto>(body);
            if (user == null || string.IsNullOrEmpty(user.id) || string.IsNullOrEmpty(user.token)) throw new Exception("Respuesta de usuario inesperada: " + Shorten(body));
            return user;
        }

        // ---------------------------------------------------------------- catálogo y mazos

        /// <summary>Todas las cartas del catálogo (la base de datos). Cada CardDto.id es el id de plantilla.</summary>
        public static async Task<List<CardDto>> GetCardsAsync(string host)
        {
            string body = await GetOk(ToHttpBase(host) + "/api/menu/cards");
            return Json.FromJson<CardList>("{\"items\":" + body + "}").items;
        }

        /// <summary>Mazos guardados del usuario de la sesión actual (requiere RestApi.Token).</summary>
        public static async Task<List<DeckDto>> GetDecksAsync(string host)
        {
            string body = await GetOk(ToHttpBase(host) + "/api/menu/decks");
            return Json.FromJson<DeckList>("{\"items\":" + body + "}").items;
        }

        /// <summary>Crea el mazo o sustituye el que tenga ese nombre. Lanza Exception con el mensaje del servidor si no es legal
        /// (40 cartas exactas, máx. 3 copias por carta, máx. 2 facciones).</summary>
        public static async Task<DeckDto> SaveDeckAsync(string host, string deckName, List<DeckEntryDto> entries)
        {
            string json = Json.ToJson(new SaveDeckRequest { deckName = deckName, entries = entries });
            var req = new HttpRequestMessage(HttpMethod.Post, ToHttpBase(host) + "/api/menu/decks") { Content = new StringContent(json, Encoding.UTF8, "application/json") };
            var resp = await Http.SendAsync(WithAuth(req));
            string body = await resp.Content.ReadAsStringAsync();
            if (!resp.IsSuccessStatusCode) throw new Exception(ErrorText(body, (int)resp.StatusCode));
            return Json.FromJson<DeckDto>(body);
        }

        public static async Task DeleteDeckAsync(string host, string deckName)
        {
            var req = new HttpRequestMessage(HttpMethod.Delete, ToHttpBase(host) + "/api/menu/decks?deckName=" + Uri.EscapeDataString(deckName));
            var resp = await Http.SendAsync(WithAuth(req));
            if (!resp.IsSuccessStatusCode) throw new Exception(ErrorText(await resp.Content.ReadAsStringAsync(), (int)resp.StatusCode));
        }

        static HttpRequestMessage WithAuth(HttpRequestMessage req)
        {
            if (!string.IsNullOrEmpty(Token)) req.Headers.TryAddWithoutValidation("Authorization", "Bearer " + Token);
            return req;
        }

        static async Task<string> GetOk(string url)
        {
            var resp = await Http.SendAsync(WithAuth(new HttpRequestMessage(HttpMethod.Get, url)));
            string body = await resp.Content.ReadAsStringAsync();
            if (!resp.IsSuccessStatusCode) throw new Exception(ErrorText(body, (int)resp.StatusCode));
            return body;
        }

        static string ErrorText(string body, int status)
        {
            try { var e = Json.FromJson<ApiError>(body); if (e != null && !string.IsNullOrEmpty(e.error)) return e.error; } catch { }
            return "El servidor respondió " + status + ". " + Shorten(body);
        }

        static string Shorten(string s) { return s != null && s.Length > 200 ? s.Substring(0, 200) + "…" : s; }
    }
}
