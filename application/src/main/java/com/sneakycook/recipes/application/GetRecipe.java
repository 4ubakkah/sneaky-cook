package com.sneakycook.recipes.application;

import com.sneakycook.recipes.domain.Recipe;
import com.sneakycook.recipes.domain.RecipeNotFoundException;
import com.sneakycook.recipes.domain.RecipeRepository;

import java.util.UUID;

/** Use case: fetch a single recipe by id [REQ-4]. */
public class GetRecipe {

    private final RecipeRepository recipes;

    public GetRecipe(RecipeRepository recipes) {
        this.recipes = recipes;
    }

    public Recipe execute(UUID id) {
        return recipes.findById(id).orElseThrow(() -> new RecipeNotFoundException(id));
    }
}
