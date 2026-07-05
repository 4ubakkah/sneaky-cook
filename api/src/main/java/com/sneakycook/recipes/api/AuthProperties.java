package com.sneakycook.recipes.api;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Auth configuration [REQ-17] (spec §13): HS256 secret from the environment
 * (dev default in application.yaml only; prod fails fast without
 * {@code JWT_SECRET}), token TTL 1 h.
 */
@ConfigurationProperties(prefix = "recipe.auth")
public record AuthProperties(String jwtSecret, Duration tokenTtl) {

    public AuthProperties {
        if (jwtSecret == null || jwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException(
                    "recipe.auth.jwt-secret must be at least 32 bytes (256 bits) for HS256");
        }
        if (tokenTtl == null || tokenTtl.isNegative() || tokenTtl.isZero()) {
            throw new IllegalArgumentException("recipe.auth.token-ttl must be a positive duration");
        }
    }
}
