package com.sneakycook.recipes.domain;

/**
 * Raised when registering a username that already exists [REQ-17]. Rendered at
 * the HTTP edge as a 409 problem document.
 */
public class UsernameTakenException extends RuntimeException {

    public UsernameTakenException(String username) {
        super("Username '" + username + "' is already taken");
    }
}
