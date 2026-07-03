package com.sneakycook.recipes.domain;

import java.util.List;
import java.util.Locale;

/**
 * Value object holding the five optional, combinable filter criteria
 * [REQ-5..REQ-10] plus paging and sorting [REQ-4]. A {@code null} criterion
 * means "not filtered on".
 *
 * <p>Ingredient criteria are lower-cased here to match the stored
 * normalization ([REQ-7], [REQ-8]): matching is plain equality against
 * lower-cased ingredient names.
 */
public record RecipeFilter(
        Boolean vegetarian,
        Integer servings,
        List<String> includeIngredients,
        List<String> excludeIngredients,
        String instructionsContain,
        int page,
        int size,
        RecipeSort sort) {

    public RecipeFilter {
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative, was " + page);
        }
        if (size < 1) {
            throw new IllegalArgumentException("size must be at least 1, was " + size);
        }
        includeIngredients = lowerCased(includeIngredients);
        excludeIngredients = lowerCased(excludeIngredients);
        if (sort == null) {
            sort = RecipeSort.NEWEST_FIRST;
        }
    }

    private static List<String> lowerCased(List<String> ingredients) {
        if (ingredients == null) {
            return List.of();
        }
        return List.copyOf(ingredients.stream()
                .map(ingredient -> ingredient.toLowerCase(Locale.ROOT))
                .toList());
    }
}
