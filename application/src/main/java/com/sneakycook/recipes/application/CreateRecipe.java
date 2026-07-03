package com.sneakycook.recipes.application;

import com.sneakycook.recipes.domain.Recipe;
import com.sneakycook.recipes.domain.RecipeRepository;

import java.time.Clock;
import java.util.List;

/**
 * Use case: add a new recipe [REQ-2]. The aggregate enforces its invariants
 * and normalizes ingredients; this class only supplies the server-managed
 * creation timestamp and persists through the port.
 */
public class CreateRecipe {

    private final RecipeRepository recipes;
    private final Clock clock;

    public CreateRecipe(RecipeRepository recipes, Clock clock) {
        this.recipes = recipes;
        this.clock = clock;
    }

    public Recipe execute(
            String name,
            boolean vegetarian,
            int servings,
            List<String> ingredients,
            String instructions) {
        Recipe recipe = Recipe.createNew(
                name, vegetarian, servings, ingredients, instructions, clock.instant());
        return recipes.save(recipe);
    }
}
