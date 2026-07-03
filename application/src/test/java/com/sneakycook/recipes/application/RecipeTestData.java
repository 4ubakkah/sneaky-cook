package com.sneakycook.recipes.application;

import com.sneakycook.recipes.domain.Recipe;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Realistic domain fixtures for use-case tests (spec §7). */
final class RecipeTestData {

    static final Instant CREATED_AT = Instant.parse("2026-07-03T12:00:00.123456Z");

    private RecipeTestData() {
    }

    static Recipe potatoGratin(UUID id) {
        return new Recipe(
                id,
                "Potato gratin",
                true,
                4,
                List.of("potatoes", "cream", "cheese", "garlic"),
                "Slice the potatoes thinly. Layer with cream, garlic and cheese in a dish. "
                        + "Bake in the oven at 180°C for 45 minutes until golden.",
                CREATED_AT);
    }
}
