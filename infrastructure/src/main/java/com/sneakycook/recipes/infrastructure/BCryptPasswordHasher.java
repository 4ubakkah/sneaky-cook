package com.sneakycook.recipes.infrastructure;

import com.sneakycook.recipes.domain.PasswordHasher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * BCrypt adapter for the {@link PasswordHasher} port [REQ-17] (spec §13).
 * Lives in infrastructure so the algorithm choice — like the database — is a
 * detail the core modules never see.
 */
@Component
class BCryptPasswordHasher implements PasswordHasher {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    /**
     * A valid hash of an unguessable value, produced with the same cost factor
     * as real hashes so {@link #matchesDecoy} costs exactly as much as a real
     * {@link #matches}. No account can ever own this hash.
     */
    private final String decoyHash = encoder.encode(UUID.randomUUID().toString());

    @Override
    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String passwordHash) {
        return encoder.matches(rawPassword, passwordHash);
    }

    @Override
    public boolean matchesDecoy(String rawPassword) {
        // Run the comparison for its timing cost, then always fail — an
        // attacker supplying the (unknown) decoy value must not be let in.
        encoder.matches(rawPassword, decoyHash);
        return false;
    }
}
