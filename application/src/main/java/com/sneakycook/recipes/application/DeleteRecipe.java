package com.sneakycook.recipes.application;

import com.sneakycook.recipes.domain.RecipeNotFoundException;
import com.sneakycook.recipes.domain.RecipeRepository;

import java.util.UUID;

/**
 * Use case: remove a recipe [REQ-3]. Hard delete; a second delete of the same
 * id is a 404, and ingredient rows go with the recipe (FK cascade).
 */
public class DeleteRecipe {

    private final RecipeRepository recipes;

    public DeleteRecipe(RecipeRepository recipes) {
        this.recipes = recipes;
    }

    public void execute(UUID id) {
        if (recipes.findById(id).isEmpty()) {
            throw new RecipeNotFoundException(id);
        }
        recipes.deleteById(id);
    }
}
