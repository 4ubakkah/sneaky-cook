package com.sneakycook.recipes.infrastructure;

import com.sneakycook.recipes.domain.Recipe;

import java.time.Instant;
import java.util.List;

/**
 * The same realistic five-recipe fixture set the E2E tier uses (spec §7),
 * expressed as domain aggregates. Distinct creation instants keep the default
 * newest-first ordering deterministic.
 */
final class DomainRecipes {

    private static final Instant BASE = Instant.parse("2026-07-03T12:00:00Z");
    private static int counter;

    private DomainRecipes() {
    }

    static Recipe potatoGratin() {
        return recipe("Potato gratin", true, 4,
                List.of("potatoes", "cream", "cheese", "garlic"),
                "Slice the potatoes thinly. Layer with cream, garlic and cheese in a dish. "
                        + "Bake in the oven at 180°C for 45 minutes until golden.");
    }

    static Recipe salmonTraybake() {
        return recipe("Salmon traybake", false, 4,
                List.of("salmon", "potatoes", "lemon", "olive oil"),
                "Toss the potatoes and salmon chunks with olive oil and lemon slices. "
                        + "Roast in the oven at 200°C for 25 minutes until the salmon flakes.");
    }

    static Recipe mushroomRisotto() {
        return recipe("Mushroom risotto", true, 2,
                List.of("arborio rice", "mushrooms", "parmesan", "white wine", "butter"),
                "Sauté the mushrooms in butter until golden. Add the rice and deglaze with white "
                        + "wine. Ladle in hot stock gradually, stirring on the stove until creamy.");
    }

    static Recipe beefStew() {
        return recipe("Beef stew", false, 6,
                List.of("beef", "carrots", "onions", "red wine", "thyme"),
                "Brown the beef in batches. Add the carrots, onions and thyme, pour in the red "
                        + "wine and simmer gently on the hob for three hours until tender.");
    }

    static Recipe vegetableSoup() {
        return recipe("Vegetable soup", true, 4,
                List.of("potatoes", "carrots", "celery", "onions"),
                "Dice all the vegetables. Sweat the onions and celery, add the potatoes and "
                        + "carrots with stock, and simmer on the stove until tender.");
    }

    static List<Recipe> allFive() {
        return List.of(potatoGratin(), salmonTraybake(), mushroomRisotto(), beefStew(), vegetableSoup());
    }

    private static Recipe recipe(
            String name, boolean vegetarian, int servings, List<String> ingredients, String instructions) {
        return Recipe.createNew(
                name, vegetarian, servings, ingredients, instructions, BASE.plusSeconds(++counter));
    }
}
