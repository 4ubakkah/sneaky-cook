package com.sneakycook.recipes.domain;

/**
 * Raised on login when the username is unknown or the password is wrong —
 * deliberately the same exception for both, so responses cannot be used to
 * enumerate registered usernames [REQ-17]. Rendered as a 401 problem document.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid username or password");
    }
}
