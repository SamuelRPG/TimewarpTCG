package com.saroga.timewarp.infrastructure.persistence.jpa.entity.User;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String username;

    /** Hash BCrypt de la contrasena. null = cuenta antigua creada cuando solo hacia falta el nombre. */
    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    // Constructores, Getters y Setters
    public UserEntity() {}
    public UserEntity(String username) { this.username = username; }
    public UserEntity(String username, String passwordHash) { this.username = username; this.passwordHash = passwordHash; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
}
