// Mini sistema de animación (sin paquetes): ejecuta "trabajos" que se actualizan cada frame con un valor 0..1.
// Anim.Run(0.4f, t => { ...; return true; });   // devuelve false para cancelar (p. ej. si el objeto ya no existe)
using System;
using System.Collections.Generic;
using UnityEngine;

namespace TimewarpTCG
{
    public sealed class AnimRunner : MonoBehaviour
    {
        sealed class Job { public float t, dur, delay; public Func<float, bool> step; public Action done; }

        static AnimRunner _inst;
        readonly List<Job> _jobs = new List<Job>();

        public static AnimRunner Instance
        {
            get
            {
                if (_inst == null)
                {
                    var go = new GameObject("TimewarpAnim");
                    DontDestroyOnLoad(go);
                    _inst = go.AddComponent<AnimRunner>();
                }
                return _inst;
            }
        }

        public void Add(float duration, Func<float, bool> step, Action done, float delay)
        {
            _jobs.Add(new Job { dur = Mathf.Max(0.0001f, duration), step = step, done = done, delay = delay });
        }

        void Update()
        {
            float dt = Time.unscaledDeltaTime;
            for (int i = _jobs.Count - 1; i >= 0; i--)
            {
                var j = _jobs[i];
                if (j.delay > 0f) { j.delay -= dt; continue; }
                j.t += dt;
                float k = Mathf.Clamp01(j.t / j.dur);
                bool keep;
                try { keep = j.step(k); }
                catch (Exception e) { Debug.LogException(e); keep = false; }
                if (!keep) { _jobs.RemoveAt(i); continue; }
                if (k >= 1f)
                {
                    _jobs.RemoveAt(i);
                    if (j.done != null) { try { j.done(); } catch (Exception e) { Debug.LogException(e); } }
                }
            }
        }
    }

    public static class Anim
    {
        public static void Run(float duration, Func<float, bool> step, Action done = null, float delay = 0f)
        {
            AnimRunner.Instance.Add(duration, step, done, delay);
        }

        public static float EaseOut(float t) { float u = 1f - t; return 1f - u * u * u; }
        public static float EaseInOut(float t) { return t < 0.5f ? 4f * t * t * t : 1f - Mathf.Pow(-2f * t + 2f, 3f) / 2f; }
        public static float EaseOutBack(float t)
        {
            const float c1 = 1.70158f, c3 = c1 + 1f;
            float u = t - 1f;
            return 1f + c3 * u * u * u + c1 * u * u;
        }
    }
}
