// Llamadas REST opcionales (UserController.java): login / registro por nombre de usuario.
// Para tu primera partida NO hacen falta: el modo "invitado" genera un id aleatorio y el backend
// acepta cualquier UUID para los mazos de prueba AGGRO / CONTROL.
using System;
using System.Net.Http;
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

        static string Shorten(string s) { return s != null && s.Length > 200 ? s.Substring(0, 200) + "…" : s; }
    }
}
