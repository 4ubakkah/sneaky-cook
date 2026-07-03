package com.sneakycook.recipes.application;

import com.sneakycook.recipes.domain.RecipeFilter;
import com.sneakycook.recipes.domain.RecipePage;
import com.sneakycook.recipes.domain.RecipeRepository;

/**
 * Use case: paged search with all optional filter criteria combined in one
 * query [REQ-4..REQ-10]. The filter value object arrives already validated by
 * the contract; this class only delegates through the port.
 */
public class ListRecipes {

    private final RecipeRepository recipes;

    public ListRecipes(RecipeRepository recipes) {
        this.recipes = recipes;
    }

    public RecipePage execute(RecipeFilter filter) {
        return recipes.search(filter);
    }
}
