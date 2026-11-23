package com.saroga.timewarp.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Flujo REST real (servidor arrancado con H2): registro, login, invitados, aislamiento entre usuarios y errores. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "timewarp.security.jwt-secret=una-clave-de-pruebas-de-mas-de-32-caracteres!!",
        "timewarp.security.auth-burst-per-ip=1000"})
public class AuthRestIntegrationTest {

    @Autowired TestRestTemplate rest;

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> post(String url, Object body, String token) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) h.setBearerAuth(token);
        return rest.exchange(url, HttpMethod.POST, new HttpEntity<>(body, h), Map.class);
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<Object> get(String url, String token) {
        HttpHeaders h = new HttpHeaders();
        if (token != null) h.setBearerAuth(token);
        return rest.exchange(url, HttpMethod.GET, new HttpEntity<>(h), Object.class);
    }

    private String register(String name) {
        ResponseEntity<Map> r = post("/api/users/register", Map.of("username", name, "password", "contraseña-segura-1"), null);
        assertEquals(200, r.getStatusCode().value(), String.valueOf(r.getBody()));
        return (String) r.getBody().get("token");
    }

    private static String uniq() { return "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 10); }

    @Test
    void registerValidatesAndRejectsDuplicates() {
        String n = uniq();
        assertEquals(400, post("/api/users/register", Map.of("username", "a", "password", "contraseña-segura-1"), null).getStatusCode().value(), "nombre corto");
        assertEquals(400, post("/api/users/register", Map.of("username", "con espacios!", "password", "contraseña-segura-1"), null).getStatusCode().value(), "caracteres no permitidos");
        assertEquals(400, post("/api/users/register", Map.of("username", n, "password", "corta"), null).getStatusCode().value(), "contraseña corta");
        assertEquals(400, post("/api/users/register", Map.of("username", n), null).getStatusCode().value(), "sin contraseña");
        register(n);
        assertEquals(409, post("/api/users/register", Map.of("username", n.toUpperCase(), "password", "contraseña-segura-1"), null).getStatusCode().value(), "duplicado sin distinguir mayusculas");
    }

    @Test
    void loginRequiresTheRightPasswordAndDoesNotRevealWhichPartFailed() {
        String n = uniq();
        register(n);
        ResponseEntity<Map> ok = post("/api/users/login", Map.of("username", n, "password", "contraseña-segura-1"), null);
        assertEquals(200, ok.getStatusCode().value());
        assertNotNull(ok.getBody().get("token"));
        ResponseEntity<Map> badPass = post("/api/users/login", Map.of("username", n, "password", "otra-contraseña-mala"), null);
        ResponseEntity<Map> noUser = post("/api/users/login", Map.of("username", uniq(), "password", "otra-contraseña-mala"), null);
        assertEquals(401, badPass.getStatusCode().value());
        assertEquals(401, noUser.getStatusCode().value());
        assertEquals(badPass.getBody().get("error"), noUser.getBody().get("error"), "mismo mensaje: no se puede averiguar quien existe");
        assertFalse(badPass.getBody().toString().contains("token"));
    }

    @Test
    void bruteForceIsLockedAfterFiveFailures() {
        String n = uniq();
        register(n);
        for (int i = 0; i < 5; i++)
            assertEquals(401, post("/api/users/login", Map.of("username", n, "password", "mala-contraseña-" + i), null).getStatusCode().value());
        assertEquals(429, post("/api/users/login", Map.of("username", n, "password", "contraseña-segura-1"), null).getStatusCode().value(),
                "bloqueado: ni siquiera con la contraseña correcta");
    }

    @Test
    void protectedEndpointsNeedAValidToken() {
        assertEquals(401, get("/api/menu/decks", null).getStatusCode().value());
        assertEquals(401, get("/api/menu/decks", "basura").getStatusCode().value());
        String t = register(uniq());
        assertEquals(401, get("/api/menu/decks", t + "x").getStatusCode().value(), "token alterado");
        assertEquals(200, get("/api/menu/decks", t).getStatusCode().value());
        assertEquals(401, rest.exchange("/api/users/me", HttpMethod.DELETE, new HttpEntity<>(new HttpHeaders()), Map.class).getStatusCode().value());
        assertEquals(200, get("/api/menu/cards", null).getStatusCode().value(), "el catalogo es publico");
        // formato antiguo (sin token, con userId en la URL) ya no sirve
        assertEquals(401, get("/api/menu/decks?userId=" + UUID.randomUUID(), null).getStatusCode().value());
    }

    @Test
    @SuppressWarnings("unchecked")
    void usersCannotSeeOrTouchEachOthersDecks() {
        String a = register(uniq()), b = register(uniq());
        List<Map<String, Object>> cards = (List<Map<String, Object>>) (List<?>) get("/api/menu/cards", null).getBody();
        // mazo legal: 13 cartas x3 + 1 (solo 2 facciones)
        java.util.Map<String, Integer> byFaction = new java.util.LinkedHashMap<>();
        List<Map<String, Object>> entries = new java.util.ArrayList<>();
        int total = 0;
        for (Map<String, Object> c : cards) {
            String f = (String) c.get("faction");
            byFaction.putIfAbsent(f, 0);
            if (byFaction.size() > 2 && byFaction.get(f) == 0 && !byFaction.keySet().stream().limit(2).anyMatch(f::equals)) continue;
            int q = Math.min(3, 40 - total);
            if (q <= 0) break;
            entries.add(Map.of("cardId", c.get("id"), "quantity", q));
            total += q;
        }
        assertEquals(40, total);
        ResponseEntity<Map> saved = post("/api/menu/decks", Map.of("deckName", "Mio", "entries", entries, "userId", UUID.randomUUID().toString()), a);
        assertEquals(200, saved.getStatusCode().value(), String.valueOf(saved.getBody()));
        assertEquals(1, ((List<?>) get("/api/menu/decks", a).getBody()).size());
        assertEquals(0, ((List<?>) get("/api/menu/decks", b).getBody()).size(), "B no ve los mazos de A (el userId del cuerpo se ignora)");
        // B no puede borrar el mazo de A
        HttpHeaders hb = new HttpHeaders(); hb.setBearerAuth(b);
        assertEquals(404, rest.exchange("/api/menu/decks?deckName=Mio", HttpMethod.DELETE, new HttpEntity<>(hb), Map.class).getStatusCode().value());
        assertEquals(1, ((List<?>) get("/api/menu/decks", a).getBody()).size());
    }

    @Test
    void guestsGetATokenButCannotKeepDecksOrAccounts() {
        ResponseEntity<Map> g = post("/api/users/guest", Map.of(), null);
        assertEquals(200, g.getStatusCode().value());
        String token = (String) g.getBody().get("token");
        assertEquals(Boolean.TRUE, g.getBody().get("guest"));
        assertEquals(200, get("/api/menu/decks", token).getStatusCode().value());
        assertEquals(403, post("/api/menu/decks", Map.of("deckName", "X", "entries", List.of()), token).getStatusCode().value());
        HttpHeaders h = new HttpHeaders(); h.setBearerAuth(token);
        assertEquals(403, rest.exchange("/api/users/me", HttpMethod.DELETE, new HttpEntity<>(h), Map.class).getStatusCode().value());
    }

    @Test
    void deleteMyAccountOnlyDeletesMine() {
        String n = uniq(), other = uniq();
        String t = register(n); register(other);
        HttpHeaders h = new HttpHeaders(); h.setBearerAuth(t);
        assertEquals(200, rest.exchange("/api/users/me", HttpMethod.DELETE, new HttpEntity<>(h), Map.class).getStatusCode().value());
        assertEquals(401, post("/api/users/login", Map.of("username", n, "password", "contraseña-segura-1"), null).getStatusCode().value(), "ya no existe");
        assertEquals(200, post("/api/users/login", Map.of("username", other, "password", "contraseña-segura-1"), null).getStatusCode().value(), "el otro sigue");
    }

    @Test
    void errorsAreGenericAndKeepTheirStatus() {
        ResponseEntity<Map> notFound = post("/api/no-existe", Map.of(), null);
        assertTrue(notFound.getStatusCode().value() == 404 || notFound.getStatusCode().value() == 405, "no debe ser 500: " + notFound.getStatusCode());
        ResponseEntity<Map> bad = rest.exchange("/api/users/login", HttpMethod.POST, new HttpEntity<>("esto no es json", jsonHeaders()), Map.class);
        assertEquals(400, bad.getStatusCode().value());
        assertFalse(String.valueOf(bad.getBody()).contains("Exception"), "sin trazas ni nombres de clases");
    }

    private static HttpHeaders jsonHeaders() { HttpHeaders h = new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON); return h; }
}
