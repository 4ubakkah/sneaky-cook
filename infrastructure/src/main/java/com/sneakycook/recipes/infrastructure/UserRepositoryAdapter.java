package com.sneakycook.recipes.infrastructure;

import com.sneakycook.recipes.domain.User;
import com.sneakycook.recipes.domain.UserRepository;
import com.sneakycook.recipes.domain.UsernameTakenException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Persistence adapter implementing the user port [REQ-17]. A lost
 * check-then-insert race lands on the {@code app_user.username} unique
 * constraint; {@code saveAndFlush} surfaces it here so it can be translated
 * into the same {@link UsernameTakenException} the use case throws — callers
 * see one failure mode, not a raw 500.
 */
@Repository
@Transactional
class UserRepositoryAdapter implements UserRepository {

    private final UserJpaRepository jpa;
    private final UserEntityMapper mapper;

    UserRepositoryAdapter(UserJpaRepository jpa, UserEntityMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public User save(User user) {
        try {
            return mapper.toDomain(jpa.saveAndFlush(mapper.toEntity(user)));
        } catch (DataIntegrityViolationException raceLostOnUniqueUsername) {
            throw new UsernameTakenException(user.username());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByUsername(String username) {
        return jpa.findByUsername(username).map(mapper::toDomain);
    }
}
