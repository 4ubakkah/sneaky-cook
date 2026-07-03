package com.sneakycook.recipes.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The recipe aggregate [REQ-2]. Owns its invariants — a {@code Recipe} instance
 * that exists is valid: non-blank name, at least one serving, at least one
 * non-blank ingredient, non-blank instructions.
 *
 * <p>Ingredient names are normalized to lower case here [REQ-7], on every
 * construction path (create and update alike), so ingredient filters match
 * case-insensitively against stored values.
 */
public record Recipe(
        UUID id,
        String name,
        boolean vegetarian,
        int servings,
        List<String> ingredients,
        String instructions,
        Instant createdAt) {

    public Recipe {
        requireNonBlank(name, "name");
        if (servings < 1) {
            throw new IllegalArgumentException("servings must be at least 1, was " + servings);
        }
        if (ingredients == null || ingredients.isEmpty()) {
            throw new IllegalArgumentException("ingredients must contain at least one item");
        }
        ingredients.forEach(ingredient -> requireNonBlank(ingredient, "ingredient"));
        requireNonBlank(instructions, "instructions");
        if (id == null || createdAt == null) {
            throw new IllegalArgumentException("id and createdAt are required");
        }
        ingredients = List.copyOf(ingredients.stream()
                .map(ingredient -> ingredient.toLowerCase(Locale.ROOT))
                .toList());
    }

    /**
     * Creates a brand-new recipe with a generated id. {@code createdAt} is
     * server-managed [REQ-2] and truncated to microseconds — PostgreSQL
     * {@code timestamptz} precision — so the value read back from the database
     * is identical to the one returned from the create response.
     */
    public static Recipe createNew(
            String name,
            boolean vegetarian,
            int servings,
            List<String> ingredients,
            String instructions,
            Instant createdAt) {
        return new Recipe(
                UUID.randomUUID(),
                name,
                vegetarian,
                servings,
                ingredients,
                instructions,
                createdAt.truncatedTo(ChronoUnit.MICROS));
    }

    /**
     * Full replacement of all client-writable fields [REQ-4 PUT semantics].
     * Identity and the server-managed {@code createdAt} are immutable — an id
     * smuggled into an update payload can never take effect because this is
     * the only update path.
     */
    public Recipe updatedWith(
            String name,
            boolean vegetarian,
            int servings,
            List<String> ingredients,
            String instructions) {
        return new Recipe(id, name, vegetarian, servings, ingredients, instructions, createdAt);
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
