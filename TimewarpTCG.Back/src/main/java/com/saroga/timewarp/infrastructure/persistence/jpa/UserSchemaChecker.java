package com.saroga.timewarp.infrastructure.persistence.jpa;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Al arrancar, comprueba que la tabla "users" tiene la columna "password_hash" (las cuentas con contrasena la necesitan).
 *
 * Con la base en memoria (H2) o con ddl-auto=update, Hibernate la crea sola. Si usas Supabase con el rol de minimo privilegio
 * (docs/supabase/rol_app.sql) o con ddl-auto=validate/none, la columna hay que añadirla UNA vez a mano: sin ella crear cuentas
 * falla. En vez de dejar un error generico, se explica aqui con el remedio exacto. Nunca impide arrancar.
 */
@Component
public class UserSchemaChecker implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    public UserSchemaChecker(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void run(ApplicationArguments args) {
        if (!hasPasswordHash()) {
            System.out.println("[CUENTAS] ATENCION: a la tabla 'users' le falta la columna 'password_hash': no se podran crear cuentas ni entrar.");
            System.out.println("[CUENTAS]   Solucion: ejecuta en Supabase (SQL Editor) el fichero docs/supabase/anadir_password_hash.sql");
            System.out.println("[CUENTAS]   (o arranca una vez con tu usuario normal y spring.jpa.hibernate.ddl-auto=update, que la crea sola).");
        }
    }

    boolean hasPasswordHash() {
        try {
            jdbc.queryForList("SELECT password_hash FROM users WHERE 1 = 0");
            return true;
        } catch (org.springframework.dao.DataAccessException e) {
            return false;
        } catch (RuntimeException e) {
            return true;    // no se ha podido comprobar (no es un error de columna): no alarmar
        }
    }
}
