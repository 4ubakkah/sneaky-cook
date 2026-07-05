package com.sneakycook.recipes.application;

import com.sneakycook.recipes.domain.PasswordHasher;
import com.sneakycook.recipes.domain.User;
import com.sneakycook.recipes.domain.UserRepository;
import com.sneakycook.recipes.domain.UsernameTakenException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegisterUserTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-07-04T09:00:00.123456789Z");
    private static final Clock CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    @Mock
    private UserRepository users;

    @Mock
    private PasswordHasher hasher;

    @Test
    @DisplayName("[REQ-17] stores the user with a hashed password, never the raw one")
    void storesHashedPassword() {
        when(users.findByUsername("alice")).thenReturn(Optional.empty());
        when(hasher.hash("correct-horse-battery")).thenReturn("$2a$10$hash");
        when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        User registered = new RegisterUser(users, hasher, CLOCK)
                .execute("alice", "correct-horse-battery");

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().username()).isEqualTo("alice");
        assertThat(saved.getValue().passwordHash()).isEqualTo("$2a$10$hash");
        assertThat(saved.getValue().passwordHash()).doesNotContain("correct-horse-battery");
        assertThat(saved.getValue().createdAt()).isEqualTo(Instant.parse("2026-07-04T09:00:00.123456Z"));
        assertThat(registered).isEqualTo(saved.getValue());
        assertThat(registered.id()).isNotNull();
    }

    @Test
    @DisplayName("[REQ-17] duplicate username → UsernameTakenException, nothing saved")
    void rejectsDuplicateUsername() {
        when(users.findByUsername("alice"))
                .thenReturn(Optional.of(User.createNew("alice", "$2a$10$existing", FIXED_NOW)));

        assertThatExceptionOfType(UsernameTakenException.class)
                .isThrownBy(() -> new RegisterUser(users, hasher, CLOCK)
                        .execute("alice", "correct-horse-battery"))
                .withMessageContaining("alice");

        verify(users, never()).save(any());
    }
}
