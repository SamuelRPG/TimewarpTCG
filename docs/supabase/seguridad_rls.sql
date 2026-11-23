-- Pegalo en Supabase > SQL Editor > Run (una sola vez, despues de haber arrancado el backend contra Supabase).
--
-- POR QUE: Supabase expone automaticamente por HTTP (API REST "PostgREST") todas las tablas del esquema "public".
-- Cualquiera que tenga la clave publica "anon" (esta pensada para ir dentro de apps) podria LEER o BORRAR tus tablas
-- si no tienen "Row Level Security" (RLS). Tu juego NO usa esa API: el backend se conecta directamente a Postgres con
-- el rol dueño de las tablas, que se salta RLS. Activando RLS sin politicas se bloquea todo acceso por la API publica
-- y el backend sigue funcionando igual (probado en un Postgres real: anon ve 0 filas, el dueño ve todo).
ALTER TABLE users          ENABLE ROW LEVEL SECURITY;
ALTER TABLE decks          ENABLE ROW LEVEL SECURITY;
ALTER TABLE deck_cards     ENABLE ROW LEVEL SECURITY;
ALTER TABLE card_templates ENABLE ROW LEVEL SECURITY;

-- Comprobacion: las 4 tablas deben salir con rowsecurity = true
SELECT tablename, rowsecurity FROM pg_tables WHERE schemaname = 'public' ORDER BY tablename;
