package com.sneakycook.recipes.domain;

import java.util.Optional;

/**
 * Persistence port for the {@link User} aggregate [REQ-17]. Implemented by the
 * infrastructure adapter; the adapter also translates a lost uniqueness race
 * into {@link UsernameTakenException} so callers see one failure mode.
 */
public interface UserRepository {

    User save(User user);

    Optional<User> findByUsername(String username);
}
