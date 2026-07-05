package com.sneakycook.recipes.domain;

/**
 * Hashing port [REQ-17]: keeps the concrete algorithm (BCrypt in the delivered
 * adapter) out of the domain and application modules, so a raw password never
 * crosses into an aggregate.
 */
public interface PasswordHasher {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String passwordHash);

    /**
     * Runs a full hash comparison against a fixed decoy hash and always reports
     * no match. Called when no account matches the supplied username, so the
     * unknown-user login path costs the same CPU as a real password check —
     * closing the timing side-channel that would otherwise let an attacker
     * enumerate accounts by measuring response latency [REQ-17].
     */
    boolean matchesDecoy(String rawPassword);
}
