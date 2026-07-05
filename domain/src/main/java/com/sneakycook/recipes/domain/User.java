package com.sneakycook.recipes.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * The user aggregate [REQ-17]. Owns its invariants — an existing {@code User}
 * has a non-blank username within the schema limit and a non-blank password
 * hash. The domain never sees a raw password: hashing happens behind the
 * {@link PasswordHasher} port before construction.
 */
public record User(
        UUID id,
        String username,
        String passwordHash,
        Instant createdAt) {

    public User {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }
        if (username.length() > 50) {
            throw new IllegalArgumentException("username must be at most 50 characters");
        }
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("passwordHash must not be blank");
        }
        if (id == null || createdAt == null) {
            throw new IllegalArgumentException("id and createdAt are required");
        }
    }

    /**
     * Creates a brand-new user with a generated id. {@code createdAt} is
     * truncated to microseconds — PostgreSQL {@code timestamptz} precision —
     * for the same read-back stability as {@link Recipe#createNew}.
     */
    public static User createNew(String username, String passwordHash, Instant createdAt) {
        return new User(
                UUID.randomUUID(),
                username,
                passwordHash,
                createdAt.truncatedTo(ChronoUnit.MICROS));
    }
}
