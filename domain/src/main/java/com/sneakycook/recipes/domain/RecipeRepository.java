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

    /**
     * Owner-scoped lookup [REQ-18]: a recipe belonging to another user is
     * indistinguishable from a nonexistent one, so foreign ids surface as 404
     * at the edge — never 403, which would leak that the id exists.
     */
    Optional<Recipe> findByIdAndOwner(UUID id, UUID ownerId);

    /** Removes the recipe; the caller has already established it exists and is owned [REQ-3]. */
    void deleteById(UUID id);

    /** Paged search applying every criterion present in the filter [REQ-4..REQ-10]; owner-scoped [REQ-18]. */
    RecipePage search(RecipeFilter filter);
}
