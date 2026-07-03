package com.sneakycook.recipes.domain;

/**
 * Sort order for recipe searches [REQ-4]. The allowed fields are fixed by the
 * API contract (`name`, `servings`, `createdAt`); anything else is rejected at
 * the edge before reaching the domain.
 */
public record RecipeSort(Field field, Direction direction) {

    /** Newest first — the default when the client sends no sort. */
    public static final RecipeSort NEWEST_FIRST = new RecipeSort(Field.CREATED_AT, Direction.DESC);

    public enum Field {
        NAME,
        SERVINGS,
        CREATED_AT
    }

    public enum Direction {
        ASC,
        DESC
    }

    public RecipeSort {
        if (field == null || direction == null) {
            throw new IllegalArgumentException("sort field and direction are required");
        }
    }
}
