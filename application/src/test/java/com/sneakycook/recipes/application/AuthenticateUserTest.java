package com.sneakycook.recipes.application;

import com.sneakycook.recipes.domain.InvalidCredentialsException;
import com.sneakycook.recipes.domain.PasswordHasher;
import com.sneakycook.recipes.domain.User;
import com.sneakycook.recipes.domain.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticateUserTest {

    private static final User ALICE = User.createNew(
            "alice", "$2a$10$hash", Instant.parse("2026-07-04T09:00:00Z"));

    @Mock
    private UserRepository users;

    @Mock
    private PasswordHasher hasher;

    @Test
    @DisplayName("[REQ-17] returns the user when the password matches its hash")
    void returnsUserOnMatchingPassword() {
        when(users.findByUsername("alice")).thenReturn(Optional.of(ALICE));
        when(hasher.matches("correct-horse-battery", "$2a$10$hash")).thenReturn(true);

        assertThat(new AuthenticateUser(users, hasher).execute("alice", "correct-horse-battery"))
                .isEqualTo(ALICE);
    }

    @Test
    @DisplayName("[REQ-17] wrong password → InvalidCredentialsException")
    void rejectsWrongPassword() {
        when(users.findByUsername("alice")).thenReturn(Optional.of(ALICE));
        when(hasher.matches("wrong", "$2a$10$hash")).thenReturn(false);

        assertThatExceptionOfType(InvalidCredentialsException.class)
                .isThrownBy(() -> new AuthenticateUser(users, hasher).execute("alice", "wrong"));
    }

    @Test
    @DisplayName("[REQ-17] unknown username → the same InvalidCredentialsException (no enumeration)")
    void rejectsUnknownUsernameIndistinguishably() {
        when(users.findByUsername("mallory")).thenReturn(Optional.empty());

        assertThatExceptionOfType(InvalidCredentialsException.class)
                .isThrownBy(() -> new AuthenticateUser(users, hasher).execute("mallory", "whatever"))
                .withMessage("Invalid username or password");
    }

    @Test
    @DisplayName("[REQ-17] unknown username still runs a decoy hash comparison (constant-time, no timing enumeration)")
    void unknownUsernameStillHashesToEqualiseTiming() {
        when(users.findByUsername("mallory")).thenReturn(Optional.empty());

        assertThatExceptionOfType(InvalidCredentialsException.class)
                .isThrownBy(() -> new AuthenticateUser(users, hasher).execute("mallory", "whatever"));

        // Without this comparison, a missing user would return faster than a
        // wrong password and leak which usernames exist.
        verify(hasher).matchesDecoy("whatever");
    }
}
