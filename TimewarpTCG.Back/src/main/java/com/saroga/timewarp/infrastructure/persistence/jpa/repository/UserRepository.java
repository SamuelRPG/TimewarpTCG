package com.saroga.timewarp.infrastructure.persistence.jpa.repository;

import com.saroga.timewarp.infrastructure.persistence.jpa.entity.User.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {
    Optional<UserEntity> findByUsername(String username);
}
