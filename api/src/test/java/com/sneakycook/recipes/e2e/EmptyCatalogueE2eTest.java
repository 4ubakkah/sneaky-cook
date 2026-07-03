package com.sneakycook.recipes.e2e;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;

/** The catalogue starts empty (base cleanup); no fixtures are seeded on purpose. */
class EmptyCatalogueE2eTest extends E2eTestBase {

    @Test
    @DisplayName("[REQ-4] GET /recipes on an empty catalogue → 200 with an empty page envelope")
    void listOnEmptyCatalogueReturnsEmptyPage() {
        given().get(RECIPES).then()
                .statusCode(200)
                .contentType("application/json")
                .body("content.size()", equalTo(0))
                .body("page", equalTo(0))
                .body("size", equalTo(20))
                .body("totalElements", equalTo(0))
                .body("totalPages", equalTo(0));
    }

    @Test
    @DisplayName("[REQ-10] filtering an empty catalogue → 200 with an empty page, not an error")
    void filteringEmptyCatalogueReturnsEmptyPage() {
        given().get(RECIPES + "?vegetarian=true&includeIngredients=potatoes").then()
                .statusCode(200)
                .body("totalElements", equalTo(0))
                .body("content.size()", equalTo(0));
    }

    @Test
    @DisplayName("[REQ-9] full-text instruction search on an empty catalogue → 200 with an empty page")
    void instructionSearchOnEmptyCatalogueReturnsEmptyPage() {
        given().get(RECIPES + "?instructionsContain=oven").then()
                .statusCode(200)
                .body("totalElements", equalTo(0))
                .body("content", empty());
    }

    @Test
    @DisplayName("[REQ-8] excludeIngredients on an empty catalogue → 200 with an empty page (nothing to exclude, no error)")
    void exclusionOnEmptyCatalogueReturnsEmptyPage() {
        given().get(RECIPES + "?excludeIngredients=salmon").then()
                .statusCode(200)
                .body("totalElements", equalTo(0))
                .body("content", empty());
    }

    @Test
    @DisplayName("[REQ-10] the full combined objective scenario on an empty catalogue → 200 with an empty page")
    void combinedObjectiveScenarioOnEmptyCatalogueReturnsEmptyPage() {
        given().get(RECIPES + "?vegetarian=true&servings=4"
                        + "&includeIngredients=potatoes&excludeIngredients=salmon&instructionsContain=oven")
                .then()
                .statusCode(200)
                .body("totalElements", equalTo(0))
                .body("totalPages", equalTo(0))
                .body("content", empty());
    }

    @Test
    @DisplayName("[REQ-4] explicit paging parameters are echoed on an empty catalogue: page=3&size=5 → page=3, size=5, totalPages=0")
    void explicitPagingParametersAreEchoedOnEmptyCatalogue() {
        given().get(RECIPES + "?page=3&size=5").then()
                .statusCode(200)
                .body("content", empty())
                .body("page", equalTo(3))
                .body("size", equalTo(5))
                .body("totalElements", equalTo(0))
                .body("totalPages", equalTo(0));
    }

    @Test
    @DisplayName("[REQ-4] sorting an empty catalogue (sort=name,asc) → 200 with an empty page, not an error")
    void sortingEmptyCatalogueReturnsEmptyPage() {
        given().get(RECIPES + "?sort=name,asc").then()
                .statusCode(200)
                .body("totalElements", equalTo(0))
                .body("content", empty());
    }

    @Test
    @DisplayName("[REQ-6] parameter validation fires even with no data: servings=0 on an empty catalogue → 400 problem document")
    void parameterValidationStillFiresOnEmptyCatalogue() {
        given().get(RECIPES + "?servings=0").then()
                .statusCode(400)
                .contentType("application/problem+json")
                .body("status", equalTo(400));
    }
}
