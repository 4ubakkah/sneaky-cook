package com.sneakycook.recipes.domain;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for the {@link Recipe} aggregate [REQ-12]. Implemented by
 * the infrastructure adapter; the domain and application layers know nothing
 * about the storage technology behind it.
 */
public interface RecipeRepository {

    Recipe save(Recipe recipe);

    Optional<Recipe> findById(UUID id);
}
