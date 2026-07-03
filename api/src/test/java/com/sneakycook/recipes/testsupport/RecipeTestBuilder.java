package com.sneakycook.recipes.testsupport;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builder for full-blown, realistic recipe request payloads (spec §7: fixtures
 * are never minimal stubs). Defaults describe a complete recipe; tests override
 * only the attribute under test.
 */
public final class RecipeTestBuilder {

    private String name = "Potato gratin";
    private Boolean vegetarian = true;
    private Integer servings = 4;
    private List<String> ingredients = List.of("potatoes", "cream", "cheese", "garlic");
    private String instructions = "Slice the potatoes thinly. Layer with cream, garlic and cheese in a dish. "
            + "Bake in the oven at 180°C for 45 minutes until golden.";

    public static RecipeTestBuilder aRecipe() {
        return new RecipeTestBuilder();
    }

    public RecipeTestBuilder withName(String name) {
        this.name = name;
        return this;
    }

    public RecipeTestBuilder withVegetarian(Boolean vegetarian) {
        this.vegetarian = vegetarian;
        return this;
    }

    public RecipeTestBuilder withServings(Integer servings) {
        this.servings = servings;
        return this;
    }

    public RecipeTestBuilder withIngredients(String... ingredients) {
        this.ingredients = List.of(ingredients);
        return this;
    }

    public RecipeTestBuilder withNoIngredients() {
        this.ingredients = List.of();
        return this;
    }

    public RecipeTestBuilder withInstructions(String instructions) {
        this.instructions = instructions;
        return this;
    }

    /** Request payload as sent over the wire. */
    public Map<String, Object> buildRequest() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("vegetarian", vegetarian);
        body.put("servings", servings);
        body.put("ingredients", ingredients);
        body.put("instructions", instructions);
        return body;
    }

    public String name() {
        return name;
    }

    public Boolean vegetarian() {
        return vegetarian;
    }

    public Integer servings() {
        return servings;
    }

    public List<String> ingredients() {
        return ingredients;
    }

    public String instructions() {
        return instructions;
    }
}
