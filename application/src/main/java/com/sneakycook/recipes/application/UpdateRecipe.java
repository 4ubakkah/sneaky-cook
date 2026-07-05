package com.sneakycook.recipes.application;

import com.sneakycook.recipes.domain.Recipe;
import com.sneakycook.recipes.domain.RecipeNotFoundException;
import com.sneakycook.recipes.domain.RecipeRepository;

import java.util.List;
import java.util.UUID;

/**
 * Use case: fully replace a recipe's client-writable fields. Identity,
 * ownership [REQ-18], and {@code createdAt} survive; the aggregate
 * re-validates and re-normalizes. A foreign recipe id behaves as nonexistent.
 */
public class UpdateRecipe {

    private final RecipeRepository recipes;

    public UpdateRecipe(RecipeRepository recipes) {
        this.recipes = recipes;
    }

    public Recipe execute(
            UUID callerId,
            UUID id,
            String name,
            boolean vegetarian,
            int servings,
            List<String> ingredients,
            String instructions) {
        Recipe existing = recipes.findByIdAndOwner(id, callerId)
                .orElseThrow(() -> new RecipeNotFoundException(id));
        return recipes.save(existing.updatedWith(name, vegetarian, servings, ingredients, instructions));
    }
}
