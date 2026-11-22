// Pequeña capa para (de)serializar JSON.
// En Unity usa JsonUtility (viene incluido, no hay que instalar nada).
// Fuera de Unity (tests de consola) usa System.Text.Json.
namespace TimewarpTCG
{
    public static class Json
    {
#if UNITY_5_3_OR_NEWER
        public static T FromJson<T>(string json) { return UnityEngine.JsonUtility.FromJson<T>(json); }
        public static string ToJson(object o) { return UnityEngine.JsonUtility.ToJson(o); }
#else
        static readonly System.Text.Json.JsonSerializerOptions Opt = new System.Text.Json.JsonSerializerOptions { IncludeFields = true };
        public static T FromJson<T>(string json) { return System.Text.Json.JsonSerializer.Deserialize<T>(json, Opt); }
        public static string ToJson(object o) { return System.Text.Json.JsonSerializer.Serialize(o, o.GetType(), Opt); }
#endif
    }
}
