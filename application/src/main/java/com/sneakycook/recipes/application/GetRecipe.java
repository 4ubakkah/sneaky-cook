package com.sneakycook.recipes.application;

import com.sneakycook.recipes.domain.Recipe;
import com.sneakycook.recipes.domain.RecipeNotFoundException;
import com.sneakycook.recipes.domain.RecipeRepository;

import java.util.UUID;

/**
 * Use case: fetch a single recipe by id [REQ-4], scoped to the calling user —
 * someone else's recipe id behaves as nonexistent [REQ-18].
 */
public class GetRecipe {

    private final RecipeRepository recipes;

    public GetRecipe(RecipeRepository recipes) {
        this.recipes = recipes;
    }

    public Recipe execute(UUID callerId, UUID id) {
        return recipes.findByIdAndOwner(id, callerId).orElseThrow(() -> new RecipeNotFoundException(id));
    }
}
