package com.saroga.timewarp.infrastructure.web.controller;

import com.saroga.timewarp.infrastructure.persistence.jpa.entity.User.UserEntity;
import com.saroga.timewarp.infrastructure.persistence.jpa.repository.DeckRepository;
import com.saroga.timewarp.infrastructure.persistence.jpa.repository.UserRepository;
import com.saroga.timewarp.infrastructure.security.AuthInterceptor;
import com.saroga.timewarp.infrastructure.security.AuthPrincipal;
import com.saroga.timewarp.infrastructure.security.JwtService;
import com.saroga.timewarp.infrastructure.security.LoginAttemptLimiter;
import com.saroga.timewarp.infrastructure.security.RateLimiter;
import com.saroga.timewarp.infrastructure.web.dto.request.AuthRequestDto;
import com.saroga.timewarp.infrastructure.web.dto.response.AuthResponseDto;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Cuentas. Registro y login con contrasena (BCrypt) -> devuelven un token firmado. "Invitado" da un token sin cuenta.
 * El resto de endpoints y el WebSocket identifican al jugador SOLO por ese token.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_-]{3,20}$");
    static final int MIN_PASSWORD = 8, MAX_PASSWORD_BYTES = 72;   // BCrypt solo usa los primeros 72 bytes

    private final UserRepository userRepository;
    private final DeckRepository deckRepository;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final LoginAttemptLimiter attempts;
    private final RateLimiter authRateLimiter;
    private final boolean allowClaimLegacy;
    private final String dummyHash;   // para gastar el mismo tiempo cuando el usuario no existe (no revelar quien existe por el tiempo de respuesta)

    public UserController(UserRepository userRepository, DeckRepository deckRepository, PasswordEncoder encoder, JwtService jwt,
                          LoginAttemptLimiter attempts, RateLimiter authRateLimiter,
                          @Value("${timewarp.security.allow-claim-legacy-accounts:false}") boolean allowClaimLegacy) {
        this.userRepository = userRepository;
        this.deckRepository = deckRepository;
        this.encoder = encoder;
        this.jwt = jwt;
        this.attempts = attempts;
        this.authRateLimiter = authRateLimiter;
        this.allowClaimLegacy = allowClaimLegacy;
        this.dummyHash = encoder.encode("no-existe-este-usuario");
    }

    // 1. REGISTRO
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody AuthRequestDto body, HttpServletRequest req) {
        if (!authRateLimiter.tryAcquire("auth:" + ip(req))) return tooMany();
        String username = body == null || body.username == null ? "" : body.username.trim();
        String password = body == null ? null : body.password;
        String problem = validate(username, password);
        if (problem != null) return error(400, problem);

        if (userRepository.findByUsernameIgnoreCase(username).isPresent()) return error(409, "El nombre de usuario ya está en uso.");
        try {
            UserEntity user = userRepository.save(new UserEntity(username, encoder.encode(password)));
            return ResponseEntity.ok(session(user.getId(), user.getUsername(), false));
        } catch (DataIntegrityViolationException e) {
            return error(409, "El nombre de usuario ya está en uso.");
        }
    }

    // 2. LOGIN
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequestDto body, HttpServletRequest req) {
        if (!authRateLimiter.tryAcquire("auth:" + ip(req))) return tooMany();
        String username = body == null || body.username == null ? "" : body.username.trim();
        String password = body == null || body.password == null ? "" : body.password;
        String key = ip(req) + "|" + username.toLowerCase();
        if (attempts.isLocked(key)) return error(429, "Demasiados intentos fallidos. Espera unos minutos y vuelve a intentarlo.");
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) { attempts.failure(key); return badCredentials(); }

        UserEntity user = userRepository.findByUsernameIgnoreCase(username).orElse(null);
        if (user == null) {
            encoder.matches(password, dummyHash);
            attempts.failure(key);
            return badCredentials();
        }
        if (user.getPasswordHash() == null) {
            // cuenta antigua, creada cuando solo hacia falta el nombre
            if (!allowClaimLegacy || password.length() < MIN_PASSWORD) {
                attempts.failure(key);
                return error(401, "Esta cuenta se creó antes de que existieran las contraseñas y no se puede usar. Crea una cuenta nueva.");
            }
            user.setPasswordHash(encoder.encode(password));
            userRepository.save(user);
        } else if (!encoder.matches(password, user.getPasswordHash())) {
            attempts.failure(key);
            return badCredentials();
        }
        attempts.success(key);
        return ResponseEntity.ok(session(user.getId(), user.getUsername(), false));
    }

    // 3. INVITADO: identidad temporal emitida por el servidor (firmada). No se guarda nada en la base de datos.
    @PostMapping("/guest")
    public ResponseEntity<?> guest(HttpServletRequest req) {
        if (!authRateLimiter.tryAcquire("auth:" + ip(req))) return tooMany();
        UUID id = UUID.randomUUID();
        return ResponseEntity.ok(session(id, "Invitado-" + id.toString().substring(0, 4), true));
    }

    // 4. ELIMINAR MI CUENTA (solo la propia): borra los mazos y luego el usuario
    @DeleteMapping("/me")
    @Transactional
    public ResponseEntity<?> deleteMyAccount(HttpServletRequest req) {
        AuthPrincipal me = AuthInterceptor.require(req);
        if (me.guest()) return error(403, "Los invitados no tienen cuenta que borrar.");
        if (!userRepository.existsById(me.userId())) return error(404, "Usuario no encontrado.");
        deckRepository.deleteByUserId(me.userId());
        userRepository.deleteById(me.userId());
        return ResponseEntity.ok(Map.of("message", "Cuenta y todos sus mazos asociados eliminados correctamente."));
    }

    // ------------------------------------------------------------------ utilidades
    private AuthResponseDto session(UUID id, String username, boolean guest) {
        return new AuthResponseDto(id.toString(), username, jwt.issue(new AuthPrincipal(id, username, guest)), guest);
    }

    static String validate(String username, String password) {
        if (!USERNAME.matcher(username).matches()) return "El nombre debe tener entre 3 y 20 caracteres: letras, números, guion o guion bajo.";
        if (password == null || password.length() < MIN_PASSWORD) return "La contraseña debe tener al menos " + MIN_PASSWORD + " caracteres.";
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) return "La contraseña es demasiado larga (máximo " + MAX_PASSWORD_BYTES + " bytes).";
        return null;
    }

    private static ResponseEntity<?> error(int status, String message) { return ResponseEntity.status(status).body(Map.of("error", message)); }
    private static ResponseEntity<?> badCredentials() { return error(401, "Usuario o contraseña incorrectos."); }
    private static ResponseEntity<?> tooMany() { return error(429, "Demasiadas peticiones. Espera un momento."); }
    private static String ip(HttpServletRequest req) { return req.getRemoteAddr(); }
}
