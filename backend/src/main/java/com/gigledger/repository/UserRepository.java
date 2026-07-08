package com.gigledger.repository;

import com.gigledger.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for User.
 * Spring auto-implements all standard CRUD methods at runtime.
 * We only need to declare custom query methods here.
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    // Used during login to look up a user by their email
    Optional<User> findByEmail(String email);

    // Used during signup to check whether the email is already taken
    boolean existsByEmail(String email);
}
