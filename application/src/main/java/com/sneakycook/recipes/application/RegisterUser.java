package com.sneakycook.recipes.application;

import com.sneakycook.recipes.domain.PasswordHasher;
import com.sneakycook.recipes.domain.User;
import com.sneakycook.recipes.domain.UserRepository;
import com.sneakycook.recipes.domain.UsernameTakenException;

import java.time.Clock;

/**
 * Use case: create an account [REQ-17]. The pre-check keeps the common
 * duplicate case a clean 409; the database unique constraint (translated by
 * the adapter) closes the check-then-insert race.
 */
public class RegisterUser {

    private final UserRepository users;
    private final PasswordHasher hasher;
    private final Clock clock;

    public RegisterUser(UserRepository users, PasswordHasher hasher, Clock clock) {
        this.users = users;
        this.hasher = hasher;
        this.clock = clock;
    }

    public User execute(String username, String rawPassword) {
        if (users.findByUsername(username).isPresent()) {
            throw new UsernameTakenException(username);
        }
        return users.save(User.createNew(username, hasher.hash(rawPassword), clock.instant()));
    }
}
