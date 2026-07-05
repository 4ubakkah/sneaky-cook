package com.sneakycook.recipes.application;

import com.sneakycook.recipes.domain.InvalidCredentialsException;
import com.sneakycook.recipes.domain.PasswordHasher;
import com.sneakycook.recipes.domain.User;
import com.sneakycook.recipes.domain.UserRepository;

import java.util.Optional;

/**
 * Use case: verify credentials [REQ-17]. Unknown username and wrong password
 * raise the same exception, so neither the response nor its timing can be used
 * to enumerate accounts: when no user is found we still run a decoy hash
 * comparison, so both failure paths spend an equal (deliberately slow) BCrypt
 * round. Token issuing is deliberately NOT here — it is an HTTP concern in the
 * api module (spec §13); this class only proves who the caller is.
 */
public class AuthenticateUser {

    private final UserRepository users;
    private final PasswordHasher hasher;

    public AuthenticateUser(UserRepository users, PasswordHasher hasher) {
        this.users = users;
        this.hasher = hasher;
    }

    public User execute(String username, String rawPassword) {
        Optional<User> found = users.findByUsername(username);
        // Both branches do one full hash comparison, so the "no such user" path
        // is indistinguishable from "wrong password" by latency.
        boolean authenticated = found
                .map(user -> hasher.matches(rawPassword, user.passwordHash()))
                .orElseGet(() -> hasher.matchesDecoy(rawPassword));
        if (!authenticated) {
            throw new InvalidCredentialsException();
        }
        return found.orElseThrow();
    }
}
