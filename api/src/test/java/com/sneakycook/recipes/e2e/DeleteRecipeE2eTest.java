package com.sneakycook.recipes.e2e;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.sneakycook.recipes.testsupport.RecipeFixtures.beefStew;
import static com.sneakycook.recipes.testsupport.RecipeFixtures.potatoGratin;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

@Tag("red")
class DeleteRecipeE2eTest extends E2eTestBase {

    private static final String UNKNOWN_ID = "6f1e2d3c-0000-0000-0000-000000000000";

    @Test
    @DisplayName("[REQ-3] DELETE /recipes/{id} → 204 with an empty body")
    void deleteReturns204WithEmptyBody() {
        String id = seed(potatoGratin());

        given().delete(RECIPES + "/" + id).then()
                .statusCode(204)
                .body(is(emptyString()));
    }

    @Test
    @DisplayName("[REQ-3] a deleted recipe is gone: subsequent GET → 404")
    void deletedRecipeIsGoneOnSubsequentGet() {
        String id = seed(potatoGratin());
        given().delete(RECIPES + "/" + id).then().statusCode(204);

        given().get(RECIPES + "/" + id).then()
                .statusCode(404)
                .contentType("application/problem+json")
                .body("status", equalTo(404))
                .body("detail", containsString(id));
    }

    @Test
    @DisplayName("[REQ-3] DELETE removes only the targeted recipe")
    void deleteLeavesOtherRecipesIntact() {
        String gratinId = seed(potatoGratin());
        String stewId = seed(beefStew());

        given().delete(RECIPES + "/" + gratinId).then().statusCode(204);

        given().get(RECIPES + "/" + stewId).then()
                .statusCode(200)
                .body("id", equalTo(stewId));
    }

    @Test
    @DisplayName("[REQ-3] DELETE /recipes/{unknown} → 404 problem document naming the id")
    void deleteUnknownIdReturns404() {
        given().delete(RECIPES + "/" + UNKNOWN_ID).then()
                .statusCode(404)
                .contentType("application/problem+json")
                .body("status", equalTo(404))
                .body("detail", containsString(UNKNOWN_ID));
    }

    @Test
    @DisplayName("[REQ-3] DELETE /recipes/{malformed-uuid} → 400 problem document")
    void deleteMalformedIdReturns400() {
        given().delete(RECIPES + "/not-a-uuid").then()
                .statusCode(400)
                .contentType("application/problem+json")
                .body("status", equalTo(400));
    }

    @Test
    @DisplayName("[REQ-3] deleting the same recipe twice: the second DELETE → 404")
    void secondDeleteReturns404() {
        String id = seed(potatoGratin());

        given().delete(RECIPES + "/" + id).then().statusCode(204);
        given().delete(RECIPES + "/" + id).then()
                .statusCode(404)
                .contentType("application/problem+json")
                .body("status", equalTo(404));
    }

    @Test
    @DisplayName("[REQ-3][REQ-4] a deleted recipe no longer appears in list results")
    void deletedRecipeDisappearsFromListResults() {
        String gratinId = seed(potatoGratin());
        String stewId = seed(beefStew());

        given().delete(RECIPES + "/" + gratinId).then().statusCode(204);

        given().get(RECIPES).then()
                .statusCode(200)
                .body("totalElements", equalTo(1))
                .body("content[0].id", equalTo(stewId));
    }
}
