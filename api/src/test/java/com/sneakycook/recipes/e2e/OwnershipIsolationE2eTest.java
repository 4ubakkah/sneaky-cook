package com.sneakycook.recipes.e2e;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.sneakycook.recipes.testsupport.RecipeFixtures.allFive;
import static com.sneakycook.recipes.testsupport.RecipeFixtures.potatoGratin;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

/**
 * [REQ-18] Recipes belong to the user who created them and are invisible to
 * everyone else. A foreign recipe id behaves exactly like a nonexistent one —
 * 404, never 403, which would leak that the id exists (spec §13).
 */
class OwnershipIsolationE2eTest extends E2eTestBase {

    @Test
    @DisplayName("[REQ-18] another user's recipe id fetches as 404 — indistinguishable from missing")
    void foreignRecipeFetchesAs404() {
        String recipeId = seed(potatoGratin()); // owned by user A (the per-test user)

        switchToNewUser(); // user B

        given().get(RECIPES + "/" + recipeId).then()
                .statusCode(404)
                .contentType("application/problem+json")
                .body("status", equalTo(404))
                .body("title", equalTo("Not Found"))
                .body("detail", containsString(recipeId));
    }

    @Test
    @DisplayName("[REQ-18] another user's recipe cannot be updated — 404, and the content is untouched")
    void foreignRecipeCannotBeUpdated() {
        TestUser owner = registerNewUser();
        String recipeId = given().auth().oauth2(owner.token())
                .contentType("application/json")
                .body(potatoGratin().buildRequest())
                .post(RECIPES)
                .then().statusCode(201)
                .extract().path("id");

        // The per-test user (a different account) attempts the update.
        given().contentType("application/json")
                .body(Map.of(
                        "name", "Hijacked recipe",
                        "vegetarian", false,
                        "servings", 1,
                        "ingredients", java.util.List.of("nothing"),
                        "instructions", "Take over the recipe."))
                .put(RECIPES + "/" + recipeId)
                .then()
                .statusCode(404)
                .contentType("application/problem+json")
                .body("status", equalTo(404));

        // Untouched for its owner — content asserted, not just the status.
        given().auth().oauth2(owner.token()).get(RECIPES + "/" + recipeId).then()
                .statusCode(200)
                .body("name", equalTo("Potato gratin"));
    }

    @Test
    @DisplayName("[REQ-18] another user's recipe cannot be deleted — 404, and it survives")
    void foreignRecipeCannotBeDeleted() {
        TestUser owner = registerNewUser();
        String recipeId = given().auth().oauth2(owner.token())
                .contentType("application/json")
                .body(potatoGratin().buildRequest())
                .post(RECIPES)
                .then().statusCode(201)
                .extract().path("id");

        given().delete(RECIPES + "/" + recipeId).then()
                .statusCode(404)
                .contentType("application/problem+json")
                .body("status", equalTo(404));

        given().auth().oauth2(owner.token()).get(RECIPES + "/" + recipeId).then()
                .statusCode(200);
    }

    @Test
    @DisplayName("[REQ-18] lists never leak across users, and page totals are per-owner")
    void listsAndTotalsArePerOwner() {
        seedAll(allFive()); // user A owns the whole fixture set

        given().get(RECIPES).then()
                .statusCode(200)
                .body("totalElements", equalTo(5));

        switchToNewUser(); // user B sees an empty catalogue

        given().get(RECIPES).then()
                .statusCode(200)
                .body("totalElements", equalTo(0))
                .body("totalPages", equalTo(0))
                .body("content.size()", equalTo(0));
    }

    @Test
    @DisplayName("[REQ-18] filters are owner-scoped too: matching foreign recipes stay invisible")
    void filtersAreOwnerScoped() {
        seed(potatoGratin()); // user A's gratin matches the query below

        switchToNewUser(); // user B runs the assignment's combined objective query

        given().get(RECIPES + "?vegetarian=true&servings=4&includeIngredients=potatoes"
                        + "&excludeIngredients=salmon&instructionsContain=oven")
                .then()
                .statusCode(200)
                .body("totalElements", equalTo(0));
    }
}
