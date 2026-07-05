package com.sneakycook.recipes.api;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Resolves the calling user's id from the verified JWT [REQ-17, REQ-18]: the
 * token's subject is the user id, and Spring Security exposes it as the
 * authentication name. The single seam through which identity enters the use
 * cases — as a plain UUID argument, keeping domain and application
 * Spring-Security-free (spec §13).
 */
@Component
class AuthenticatedUser {

    UUID id() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return UUID.fromString(authentication.getName());
    }
}
