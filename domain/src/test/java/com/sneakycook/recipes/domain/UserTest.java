package com.sneakycook.recipes.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/** Invariants of the {@link User} aggregate [REQ-17]. */
class UserTest {

    private static final Instant NOW = Instant.parse("2026-07-04T09:00:00.123456789Z");
    private static final String HASH = "$2a$10$abcdefghijklmnopqrstuvwxyz012345678901234567890123456";

    @ParameterizedTest(name = "[REQ-17] blank username ''{0}'' is rejected")
    @ValueSource(strings = {"", "   ", "\t"})
    void rejectsBlankUsername(String username) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> User.createNew(username, HASH, NOW))
                .withMessageContaining("username");
    }

    @Test
    @DisplayName("[REQ-17] username longer than the 50-character schema limit is rejected")
    void rejectsOverlongUsername() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> User.createNew("a".repeat(51), HASH, NOW))
                .withMessageContaining("50");
    }

    @Test
    @DisplayName("[REQ-17] blank password hash is rejected — hashing happens before construction")
    void rejectsBlankPasswordHash() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> User.createNew("alice", "  ", NOW))
                .withMessageContaining("passwordHash");
    }

    @Test
    @DisplayName("[REQ-17] id and createdAt are required")
    void rejectsMissingIdOrCreatedAt() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new User(null, "alice", HASH, NOW));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new User(UUID.randomUUID(), "alice", HASH, null));
    }

    @Test
    @DisplayName("[REQ-17] createNew generates an id and truncates createdAt to microseconds")
    void createNewGeneratesIdentity() {
        User user = User.createNew("alice", HASH, NOW);

        assertThat(user.id()).isNotNull();
        assertThat(user.username()).isEqualTo("alice");
        assertThat(user.passwordHash()).isEqualTo(HASH);
        assertThat(user.createdAt()).isEqualTo(Instant.parse("2026-07-04T09:00:00.123456Z"));
    }
}
