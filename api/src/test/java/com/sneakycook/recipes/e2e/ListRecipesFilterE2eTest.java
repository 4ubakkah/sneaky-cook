package com.sneakycook.recipes.e2e;

import com.sneakycook.recipes.testsupport.RecipeFixtures;
import com.sneakycook.recipes.testsupport.RecipeTestBuilder;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Filter behavior through the public API against the full fixture set — every
 * criterion has recipes it must match AND recipes it must reject (spec §7).
 */
class ListRecipesFilterE2eTest extends E2eTestBase {

    private RecipeTestBuilder gratin;

    @BeforeEach
    void seedAllFiveRecipes() {
        gratin = RecipeFixtures.potatoGratin();
        seedAll(RecipeFixtures.allFive());
    }

    @Test
    @DisplayName("[REQ-4] GET /recipes without filters returns all recipes")
    void listWithoutFiltersReturnsAllRecipes() {
        given().get(RECIPES).then()
                .statusCode(200)
                .contentType("application/json")
                .body("totalElements", equalTo(5))
                .body("content.name", containsInAnyOrder(
                        "Potato gratin", "Salmon traybake", "Mushroom risotto", "Beef stew", "Vegetable soup"));
    }

    @Test
    @DisplayName("[REQ-4] GET /recipes without paging parameters applies the defaults page=0, size=20")
    void listAppliesDefaultPaging() {
        given().get(RECIPES).then()
                .statusCode(200)
                .body("page", equalTo(0))
                .body("size", equalTo(20))
                .body("totalPages", equalTo(1));
    }

    @Test
    @DisplayName("[REQ-5] vegetarian=true returns only vegetarian recipes")
    void filterVegetarianTrue() {
        given().get(RECIPES + "?vegetarian=true").then()
                .statusCode(200)
                .body("totalElements", equalTo(3))
                .body("content.name", containsInAnyOrder("Potato gratin", "Mushroom risotto", "Vegetable soup"))
                .body("content.vegetarian", contains(true, true, true));
    }

    @Test
    @DisplayName("[REQ-5] vegetarian=false returns only non-vegetarian recipes")
    void filterVegetarianFalse() {
        given().get(RECIPES + "?vegetarian=false").then()
                .statusCode(200)
                .body("totalElements", equalTo(2))
                .body("content.name", containsInAnyOrder("Salmon traybake", "Beef stew"))
                .body("content.vegetarian", contains(false, false));
    }

    @Test
    @DisplayName("[REQ-6] servings=4 returns only recipes for exactly 4")
    void filterByServings() {
        given().get(RECIPES + "?servings=4").then()
                .statusCode(200)
                .body("totalElements", equalTo(3))
                .body("content.name", containsInAnyOrder("Potato gratin", "Salmon traybake", "Vegetable soup"))
                .body("content.servings", contains(4, 4, 4));
    }

    @Test
    @DisplayName("[REQ-7] includeIngredients=potatoes returns only recipes containing potatoes")
    void filterByIncludedIngredient() {
        Response response = given().get(RECIPES + "?includeIngredients=potatoes");

        response.then()
                .statusCode(200)
                .body("totalElements", equalTo(3))
                .body("content.name", containsInAnyOrder("Potato gratin", "Salmon traybake", "Vegetable soup"));

        List<List<String>> ingredients = response.path("content.ingredients");
        assertThat(ingredients).allMatch(list -> list.contains("potatoes"));
    }

    @Test
    @DisplayName("[REQ-7] multiple includeIngredients use AND semantics: all must be present")
    void multipleIncludedIngredientsAreAndSemantics() {
        Response response = given().get(RECIPES + "?includeIngredients=potatoes&includeIngredients=carrots");

        response.then()
                .statusCode(200)
                .body("totalElements", equalTo(1))
                // Gratin and traybake have potatoes only, stew has carrots only —
                // OR semantics would return all of them.
                .body("content.name", not(hasItems("Potato gratin", "Salmon traybake", "Beef stew")))
                .body("content.name", contains("Vegetable soup"));

        List<List<String>> ingredients = response.path("content.ingredients");
        assertThat(ingredients).allMatch(list -> list.containsAll(List.of("potatoes", "carrots")));
    }

    @Test
    @DisplayName("[REQ-7] ingredient matching is case-insensitive: includeIngredients=Potatoes matches lowercase-stored potatoes")
    void includedIngredientMatchIsCaseInsensitive() {
        Response response = given().get(RECIPES + "?includeIngredients=Potatoes");

        response.then()
                .statusCode(200)
                .body("totalElements", equalTo(3))
                .body("content.name", containsInAnyOrder("Potato gratin", "Salmon traybake", "Vegetable soup"));

        // Ingredients are stored lower-cased; the capitalized query must still match them.
        List<List<String>> ingredients = response.path("content.ingredients");
        assertThat(ingredients).allMatch(list -> list.contains("potatoes"));
    }

    @Test
    @DisplayName("[REQ-7] includeIngredients with an ingredient no recipe has → empty page, not an error")
    void includeUnknownIngredientReturnsEmptyPage() {
        given().get(RECIPES + "?includeIngredients=dragonfruit").then()
                .statusCode(200)
                .body("totalElements", equalTo(0))
                .body("content.size()", equalTo(0));
    }

    @Test
    @DisplayName("[REQ-8] excludeIngredients with an ingredient no recipe has excludes nothing: all recipes returned")
    void excludeUnknownIngredientReturnsEverything() {
        given().get(RECIPES + "?excludeIngredients=dragonfruit").then()
                .statusCode(200)
                .body("totalElements", equalTo(5));
    }

    @Test
    @DisplayName("[REQ-8] exclusion matching is case-insensitive: excludeIngredients=Salmon rejects the traybake")
    void excludedIngredientMatchIsCaseInsensitive() {
        given().get(RECIPES + "?excludeIngredients=Salmon").then()
                .statusCode(200)
                .body("totalElements", equalTo(4))
                .body("content.name", not(hasItem("Salmon traybake")));
    }

    @Test
    @DisplayName("[REQ-8] multiple excludeIngredients use NONE semantics: recipes with either salmon or beef are rejected")
    void multipleExcludedIngredientsAreNoneSemantics() {
        Response response = given().get(RECIPES + "?excludeIngredients=salmon&excludeIngredients=beef");

        response.then()
                .statusCode(200)
                .body("totalElements", equalTo(3))
                // Traybake has salmon, stew has beef — one excluded ingredient
                // each is enough to reject; neither has both.
                .body("content.name", not(hasItems("Salmon traybake", "Beef stew")))
                .body("content.name", containsInAnyOrder(
                        "Potato gratin", "Mushroom risotto", "Vegetable soup"));

        List<List<String>> ingredients = response.path("content.ingredients");
        assertThat(ingredients).noneMatch(list -> list.contains("salmon") || list.contains("beef"));
    }

    @Test
    @DisplayName("[REQ-8] excludeIngredients=salmon: no returned recipe contains salmon")
    void filterByExcludedIngredient() {
        Response response = given().get(RECIPES + "?excludeIngredients=salmon");

        response.then()
                .statusCode(200)
                .body("totalElements", equalTo(4))
                .body("content.name", containsInAnyOrder(
                        "Potato gratin", "Mushroom risotto", "Beef stew", "Vegetable soup"));

        // Assert on returned content, not just counts (spec §7)
        List<List<String>> ingredients = response.path("content.ingredients");
        assertThat(ingredients).noneMatch(list -> list.contains("salmon"));
    }

    @Test
    @DisplayName("[REQ-7][REQ-8] include and exclude interact correctly: with potatoes but without salmon")
    void includeAndExcludeInteraction() {
        Response response = given().get(RECIPES + "?includeIngredients=potatoes&excludeIngredients=salmon");

        response.then()
                .statusCode(200)
                .body("totalElements", equalTo(2))
                .body("content.name", containsInAnyOrder("Potato gratin", "Vegetable soup"));

        List<List<String>> ingredients = response.path("content.ingredients");
        assertThat(ingredients).allMatch(list -> list.contains("potatoes"));
        assertThat(ingredients).noneMatch(list -> list.contains("salmon"));
    }

    @Test
    @DisplayName("[REQ-9] instructionsContain=oven matches only recipes mentioning the oven")
    void filterByInstructionText() {
        Response response = given().get(RECIPES + "?instructionsContain=oven");

        response.then()
                .statusCode(200)
                .body("totalElements", equalTo(2))
                .body("content.name", containsInAnyOrder("Potato gratin", "Salmon traybake"));

        // Every returned recipe's instructions actually mention the searched word.
        List<String> instructions = response.path("content.instructions");
        assertThat(instructions).allMatch(text -> text.toLowerCase().contains("oven"));
    }

    @Test
    @DisplayName("[REQ-9] instruction search is word-stemmed: 'roasting' matches 'Roast'")
    void instructionSearchIsStemmed() {
        given().get(RECIPES + "?instructionsContain=roasting").then()
                .statusCode(200)
                .body("totalElements", equalTo(1))
                .body("content[0].name", equalTo("Salmon traybake"));
    }

    @Test
    @DisplayName("[REQ-9] multi-word instruction search requires all words: 'bake oven' matches only the gratin")
    void multiWordInstructionSearchRequiresAllWords() {
        given().queryParam("instructionsContain", "bake oven").get(RECIPES).then()
                .statusCode(200)
                .body("totalElements", equalTo(1))
                .body("content[0].name", equalTo("Potato gratin"));
    }

    @Test
    @DisplayName("[REQ-9] instruction search with no matches → empty page, not an error")
    void instructionSearchWithNoMatchReturnsEmptyPage() {
        given().get(RECIPES + "?instructionsContain=microwave").then()
                .statusCode(200)
                .body("totalElements", equalTo(0))
                .body("content.size()", equalTo(0));
    }

    @Test
    @DisplayName("[REQ-10] combined objective scenario: vegetarian, 4 servings, with potatoes, without salmon, mentioning oven → exactly the potato gratin")
    void combinedFilterScenarioFromTheObjective() {
        given().get(RECIPES + "?vegetarian=true&servings=4"
                        + "&includeIngredients=potatoes&excludeIngredients=salmon&instructionsContain=oven")
                .then()
                .statusCode(200)
                .body("totalElements", equalTo(1))
                .body("totalPages", equalTo(1))
                .body("content[0].id", notNullValue())
                .body("content[0].name", equalTo(gratin.name()))
                .body("content[0].vegetarian", equalTo(gratin.vegetarian()))
                .body("content[0].servings", equalTo(gratin.servings()))
                .body("content[0].ingredients", contains(gratin.ingredients().toArray()))
                .body("content[0].instructions", equalTo(gratin.instructions()))
                .body("content[0].createdAt", notNullValue());
    }

    @Test
    @DisplayName("[REQ-10] combined filters that no recipe satisfies return an empty page, not an error")
    void combinedFiltersWithNoMatchReturnEmptyPage() {
        given().get(RECIPES + "?vegetarian=true&servings=6&includeIngredients=salmon").then()
                .statusCode(200)
                .body("totalElements", equalTo(0))
                .body("totalPages", equalTo(0))
                .body("content.size()", equalTo(0));
    }

    @Test
    @DisplayName("[REQ-4] bad filter parameter type (servings=abc) → 400 problem document")
    void badParameterTypeReturns400() {
        // Detail names the parameter, the expected type, and the offending
        // value — the contract's documented BadParameterProblem example.
        given().get(RECIPES + "?servings=abc").then()
                .statusCode(400)
                .contentType("application/problem+json")
                .body("status", equalTo(400))
                .body("detail", equalTo("Parameter 'servings' must be an integer, got 'abc'"));
    }

    @Test
    @DisplayName("[REQ-6] servings=0 violates the contract minimum → 400")
    void servingsBelowMinimumReturns400() {
        given().get(RECIPES + "?servings=0").then()
                .statusCode(400)
                .contentType("application/problem+json")
                .body("status", equalTo(400));
    }
}
