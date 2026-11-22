package com.saroga.timewarp.infrastructure.web.controller;

import com.saroga.timewarp.infrastructure.persistence.jpa.entity.User.UserEntity;
import com.saroga.timewarp.infrastructure.persistence.jpa.repository.DeckRepository;
import com.saroga.timewarp.infrastructure.persistence.jpa.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
public class UserController {

    private final UserRepository userRepository;
    private final DeckRepository deckRepository;

    // Inyectamos también el repositorio de mazos
    public UserController(UserRepository userRepository, DeckRepository deckRepository) {
        this.userRepository = userRepository;
        this.deckRepository = deckRepository;
    }

    // 1. REGISTRO: Falla si el nombre ya existe
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestParam("username") String username) {
        if (userRepository.findByUsername(username).isPresent()) {
            return ResponseEntity.badRequest().body("{\"error\": \"El nombre de usuario ya está en uso.\"}");
        }

        UserEntity newUser = new UserEntity(username);
        return ResponseEntity.ok(userRepository.save(newUser));
    }

    // 2. LOGIN: Falla si el nombre NO existe
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestParam("username") String username) {
        Optional<UserEntity> existingUser = userRepository.findByUsername(username);

        if (existingUser.isPresent()) {
            return ResponseEntity.ok(existingUser.get());
        } else {
            return ResponseEntity.status(404).body("{\"error\": \"Usuario no encontrado. Por favor, regístrate primero.\"}");
        }
    }

    // 3. ELIMINAR CUENTA: Borra los mazos y luego el usuario
    @DeleteMapping("/{userId}")
    @Transactional // Esto asegura que si algo falla a medias, no se borra nada (Todo o nada)
    public ResponseEntity<?> deleteAccount(@PathVariable("userId") UUID userId) {
        if (!userRepository.existsById(userId)) {
            return ResponseEntity.status(404).body("{\"error\": \"Usuario no encontrado.\"}");
        }

        // Paso A: Borrar todos los mazos del jugador para evitar errores de clave foránea en Supabase
        deckRepository.deleteByUserId(userId);

        // Paso B: Borrar la cuenta del jugador
        userRepository.deleteById(userId);

        return ResponseEntity.ok("{\"message\": \"Cuenta y todos sus mazos asociados eliminados correctamente.\"}");
    }
}
