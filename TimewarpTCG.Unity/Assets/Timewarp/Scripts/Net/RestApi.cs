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

        /// <summary>Entra con ese nombre; si no existe (404) lo registra. Devuelve el usuario con su UUID.</summary>
        public static async Task<UserDto> LoginOrRegisterAsync(string host, string username)
        {
            string baseUrl = ToHttpBase(host);
            string q = "?username=" + Uri.EscapeDataString(username);

            HttpResponseMessage resp = await Http.PostAsync(baseUrl + "/api/users/login" + q, new StringContent(""));
            if ((int)resp.StatusCode == 404)
                resp = await Http.PostAsync(baseUrl + "/api/users/register" + q, new StringContent(""));

            string body = await resp.Content.ReadAsStringAsync();
            if (!resp.IsSuccessStatusCode)
                throw new Exception("El servidor respondió " + (int)resp.StatusCode + " en usuarios. " + Shorten(body));

            var user = Json.FromJson<UserDto>(body);
            if (user == null || string.IsNullOrEmpty(user.id)) throw new Exception("Respuesta de usuario inesperada: " + Shorten(body));
            return user;
        }

        // ---------------------------------------------------------------- catálogo y mazos

        /// <summary>Todas las cartas del catálogo (la base de datos). Cada CardDto.id es el id de plantilla.</summary>
        public static async Task<List<CardDto>> GetCardsAsync(string host)
        {
            string body = await GetOk(ToHttpBase(host) + "/api/menu/cards");
            return Json.FromJson<CardList>("{\"items\":" + body + "}").items;
        }

        /// <summary>Mazos guardados del usuario.</summary>
        public static async Task<List<DeckDto>> GetDecksAsync(string host, string userId)
        {
            string body = await GetOk(ToHttpBase(host) + "/api/menu/decks?userId=" + Uri.EscapeDataString(userId));
            return Json.FromJson<DeckList>("{\"items\":" + body + "}").items;
        }

        /// <summary>Crea el mazo o sustituye el que tenga ese nombre. Lanza Exception con el mensaje del servidor si no es legal
        /// (40 cartas exactas, máx. 3 copias por carta, máx. 2 facciones).</summary>
        public static async Task<DeckDto> SaveDeckAsync(string host, string userId, string deckName, List<DeckEntryDto> entries)
        {
            string json = Json.ToJson(new SaveDeckRequest { userId = userId, deckName = deckName, entries = entries });
            var resp = await Http.PostAsync(ToHttpBase(host) + "/api/menu/decks", new StringContent(json, Encoding.UTF8, "application/json"));
            string body = await resp.Content.ReadAsStringAsync();
            if (!resp.IsSuccessStatusCode) throw new Exception(ErrorText(body, (int)resp.StatusCode));
            return Json.FromJson<DeckDto>(body);
        }

        public static async Task DeleteDeckAsync(string host, string userId, string deckName)
        {
            var resp = await Http.DeleteAsync(ToHttpBase(host) + "/api/menu/decks?userId=" + Uri.EscapeDataString(userId) + "&deckName=" + Uri.EscapeDataString(deckName));
            if (!resp.IsSuccessStatusCode) throw new Exception(ErrorText(await resp.Content.ReadAsStringAsync(), (int)resp.StatusCode));
        }

        static async Task<string> GetOk(string url)
        {
            var resp = await Http.GetAsync(url);
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
