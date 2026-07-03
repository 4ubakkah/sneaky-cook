package com.sneakycook.recipes.testsupport;

import java.util.List;

import static com.sneakycook.recipes.testsupport.RecipeTestBuilder.aRecipe;

/**
 * The realistic fixture set (spec §7): deliberately designed so every filter
 * criterion has matching AND non-matching recipes.
 *
 * <pre>
 * vegetarian=true          → gratin, risotto, soup      (not: traybake, stew)
 * servings=4               → gratin, traybake, soup     (not: risotto=2, stew=6)
 * include potatoes         → gratin, traybake, soup
 * include potatoes+carrots → soup only (AND semantics)
 * exclude salmon           → all but traybake
 * instructions "oven"      → gratin, traybake           (others cook on the stove)
 * combined objective query → gratin only
 * </pre>
 */
public final class RecipeFixtures {

    private RecipeFixtures() {
    }

    public static RecipeTestBuilder potatoGratin() {
        return aRecipe(); // builder defaults ARE the potato gratin
    }

    public static RecipeTestBuilder salmonTraybake() {
        return aRecipe()
                .withName("Salmon traybake")
                .withVegetarian(false)
                .withServings(4)
                .withIngredients("salmon", "potatoes", "lemon", "olive oil")
                .withInstructions("Toss the potatoes and salmon chunks with olive oil and lemon slices. "
                        + "Roast in the oven at 200°C for 25 minutes until the salmon flakes.");
    }

    public static RecipeTestBuilder mushroomRisotto() {
        return aRecipe()
                .withName("Mushroom risotto")
                .withVegetarian(true)
                .withServings(2)
                .withIngredients("arborio rice", "mushrooms", "parmesan", "white wine", "butter")
                .withInstructions("Sauté the mushrooms in butter until golden. Add the rice and deglaze "
                        + "with white wine. Ladle in hot stock gradually, stirring on the stove until creamy. "
                        + "Finish with grated parmesan.");
    }

    public static RecipeTestBuilder beefStew() {
        return aRecipe()
                .withName("Beef stew")
                .withVegetarian(false)
                .withServings(6)
                .withIngredients("beef", "carrots", "onions", "red wine", "thyme")
                .withInstructions("Brown the beef in batches. Add the carrots, onions and thyme, pour in "
                        + "the red wine and simmer gently on the hob for three hours until tender.");
    }

    public static RecipeTestBuilder vegetableSoup() {
        return aRecipe()
                .withName("Vegetable soup")
                .withVegetarian(true)
                .withServings(4)
                .withIngredients("potatoes", "carrots", "celery", "onions")
                .withInstructions("Dice all the vegetables. Sweat the onions and celery, add the potatoes "
                        + "and carrots with stock, and simmer on the stove until tender. Blend until smooth.");
    }

    public static List<RecipeTestBuilder> allFive() {
        return List.of(potatoGratin(), salmonTraybake(), mushroomRisotto(), beefStew(), vegetableSoup());
    }
}
