-- Rol de BASE DE DATOS con los permisos justos para el backend ("minimo privilegio").
-- Pegalo en Supabase > SQL Editor > Run (una vez, DESPUES de haber arrancado el backend al menos una vez con tu usuario
-- normal "postgres", para que Hibernate cree/actualice las tablas, y DESPUES de seguridad_rls.sql).
--
-- POR QUE: ahora mismo el backend se conecta como "postgres", el dueño de todo. Si alguien consigue ejecutar SQL a traves
-- de un fallo del backend, con ese usuario podria borrar o cambiar CUALQUIER cosa de tu proyecto (tablas, politicas...).
-- Con este rol solo puede leer/escribir las 4 tablas del juego: no puede crear, modificar ni borrar tablas.
--
-- ANTES DE EJECUTARLO: cambia la contraseña de abajo por una larga y unica (no la reutilices ni la subas a git).

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'timewarp_app') THEN
    CREATE ROLE timewarp_app LOGIN PASSWORD 'CAMBIA-ESTA-CONTRASENA-por-una-larga-y-unica';
  END IF;
END
$$;

GRANT USAGE ON SCHEMA public TO timewarp_app;
REVOKE CREATE ON SCHEMA public FROM timewarp_app;

-- cuentas y mazos: lectura y escritura
GRANT SELECT, INSERT, UPDATE, DELETE ON users, decks, deck_cards TO timewarp_app;
-- el catalogo de cartas: el backend solo lo LEE (las cartas las cargas tu con cartas.sql desde el panel)
GRANT SELECT ON card_templates TO timewarp_app;
-- el contador (id autonumerico) de deck_cards
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO timewarp_app;

-- Como las tablas tienen RLS activado (seguridad_rls.sql), un rol que no es el dueño necesita una politica que le deje pasar.
-- "anon" y "authenticated" (la API publica de Supabase) NO tienen politica, asi que siguen sin ver nada.
DROP POLICY IF EXISTS timewarp_app_all ON users;
DROP POLICY IF EXISTS timewarp_app_all ON decks;
DROP POLICY IF EXISTS timewarp_app_all ON deck_cards;
DROP POLICY IF EXISTS timewarp_app_read ON card_templates;
CREATE POLICY timewarp_app_all  ON users          FOR ALL    TO timewarp_app USING (true) WITH CHECK (true);
CREATE POLICY timewarp_app_all  ON decks          FOR ALL    TO timewarp_app USING (true) WITH CHECK (true);
CREATE POLICY timewarp_app_all  ON deck_cards     FOR ALL    TO timewarp_app USING (true) WITH CHECK (true);
CREATE POLICY timewarp_app_read ON card_templates FOR SELECT TO timewarp_app USING (true);

-- Comprobacion: debe listar solo permisos sobre esas 4 tablas
SELECT table_name, string_agg(privilege_type, ', ' ORDER BY privilege_type) AS permisos
FROM information_schema.role_table_grants
WHERE grantee = 'timewarp_app' AND table_schema = 'public'
GROUP BY table_name ORDER BY table_name;

-- COMO USARLO (en tu ordenador):
--   1. En supabase.env.cmd pon como usuario "timewarp_app" y su contraseña (en el pooler de Supabase el usuario se escribe
--      timewarp_app.<ref-de-tu-proyecto>; el "ref" es el trozo que ves en el host de tu cadena de conexion).
--   2. Añade tambien   set SPRING_JPA_HIBERNATE_DDL_AUTO=validate   para que el backend solo COMPRUEBE las tablas (con este
--      rol no puede ni debe cambiarlas). Cuando tengas que cambiar el modelo, vuelve un momento a "postgres" y "update".
