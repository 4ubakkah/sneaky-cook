package com.sneakycook.recipes.domain;

import java.util.UUID;

/**
 * Raised when a recipe id does not exist [REQ-4]. Rendered at the HTTP edge as
 * a 404 problem document whose detail names the id.
 */
public class RecipeNotFoundException extends RuntimeException {

    public RecipeNotFoundException(UUID id) {
        super("No recipe with id " + id);
    }
}
