-- Añade a la tabla "users" la columna donde se guarda la contraseña cifrada (BCrypt) de cada cuenta.
-- Pegalo en Supabase > SQL Editor > Run. Es seguro repetirlo.
--
-- CUANDO HACE FALTA: normalmente Hibernate crea esta columna solo la primera vez que arrancas el backend con tu usuario de siempre
-- (ddl-auto=update). Hay que hacerlo a mano si arrancaste ya con el rol de minimo privilegio (rol_app.sql) o con
-- ddl-auto=validate, porque entonces el backend no puede cambiar las tablas. Sintoma: al pulsar CREAR CUENTA sale
-- "Error de base de datos", y la consola del backend avisa con "[CUENTAS] ATENCION ... password_hash".
--
-- Nota: NO se crea ninguna tabla nueva; las cuentas siguen en "users". Las cuentas antiguas (sin contraseña) quedan con la
-- columna vacia y no pueden entrar hasta que crees una cuenta nueva (ver docs/GUIA_ARRANQUE.md, punto 6).
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_hash varchar(100);

-- Comprobacion: debe devolver una fila
SELECT column_name, data_type FROM information_schema.columns
WHERE table_name = 'users' AND column_name = 'password_hash';
