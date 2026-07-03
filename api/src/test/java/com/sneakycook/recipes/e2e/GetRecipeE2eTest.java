package com.sneakycook.recipes.e2e;

import com.sneakycook.recipes.testsupport.RecipeTestBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.sneakycook.recipes.testsupport.RecipeFixtures.mushroomRisotto;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@Tag("red")
class GetRecipeE2eTest extends E2eTestBase {

    private static final String UNKNOWN_ID = "6f1e2d3c-0000-0000-0000-000000000000";

    @Test
    @DisplayName("[REQ-4] GET /recipes/{id} returns the recipe with all fields")
    void getByIdReturnsFullRecipe() {
        RecipeTestBuilder risotto = mushroomRisotto();
        String id = seed(risotto);

        given().get(RECIPES + "/" + id).then()
                .statusCode(200)
                .contentType("application/json")
                .body("id", equalTo(id))
                .body("name", equalTo(risotto.name()))
                .body("vegetarian", equalTo(risotto.vegetarian()))
                .body("servings", equalTo(risotto.servings()))
                .body("ingredients", contains(risotto.ingredients().toArray()))
                .body("instructions", equalTo(risotto.instructions()))
                .body("createdAt", notNullValue());
    }

    @Test
    @DisplayName("[REQ-4] GET /recipes/{unknown} → 404 problem document naming the id")
    void getUnknownIdReturns404ProblemDocument() {
        given().get(RECIPES + "/" + UNKNOWN_ID).then()
                .statusCode(404)
                .contentType("application/problem+json")
                .body("status", equalTo(404))
                .body("title", equalTo("Not Found"))
                .body("detail", containsString(UNKNOWN_ID))
                .body("instance", equalTo("/api/v1/recipes/" + UNKNOWN_ID));
    }

    @Test
    @DisplayName("[REQ-4] GET /recipes/{malformed-uuid} → 400 problem document")
    void getMalformedIdReturns400() {
        given().get(RECIPES + "/not-a-uuid").then()
                .statusCode(400)
                .contentType("application/problem+json")
                .body("status", equalTo(400));
    }
}
