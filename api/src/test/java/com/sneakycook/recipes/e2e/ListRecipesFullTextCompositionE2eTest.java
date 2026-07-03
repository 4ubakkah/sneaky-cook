package com.sneakycook.recipes.e2e;

import com.sneakycook.recipes.testsupport.RecipeFixtures;
import com.sneakycook.recipes.testsupport.RecipeTestBuilder;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.sneakycook.recipes.testsupport.RecipeTestBuilder.aRecipe;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;

/**
 * Full-text search composed with the other list-recipe criteria and sort/paging
 * through the public API [REQ-4, REQ-9, REQ-10]. Mechanism-level edge cases
 * (stemming, websearch syntax, ts_rank math) stay in the specification tier;
 * here we prove the HTTP contract wires everything together.
 */
class ListRecipesFullTextCompositionE2eTest extends E2eTestBase {

    @BeforeEach
    void seedStandardFixtures() {
        seedAll(RecipeFixtures.allFive());
    }

    @Nested
    @DisplayName("default relevance sort")
    class DefaultRelevanceSort {

        @Test
        @DisplayName("[REQ-9] instructionsContain without sort orders by full-text rank, not createdAt")
        void instructionsContainDefaultsToRelevanceSort() {
            deleteAllThroughApi();
            seed(aRecipe()
                    .withName("Z heavy quokka")
                    .withInstructions("Quokka quokka quokka — fold quokka through the sauce until glossy."));
            seed(aRecipe()
                    .withName("A light quokka")
                    .withInstructions("Fold in the quokka once at the end."));

            given().queryParam("instructionsContain", "quokka").get(RECIPES).then()
                    .statusCode(200)
                    .body("content.name", contains("Z heavy quokka", "A light quokka"));
        }
    }

    @Nested
    @DisplayName("explicit sort overrides relevance")
    class ExplicitSortOverridesRelevance {

        @BeforeEach
        void seedRankedPair() {
            deleteAllThroughApi();
            seed(aRecipe()
                    .withName("Z heavy quokka")
                    .withInstructions("Quokka quokka quokka — fold quokka through the sauce until glossy."));
            seed(aRecipe()
                    .withName("A light quokka")
                    .withInstructions("Fold in the quokka once at the end."));
        }

        @Test
        @DisplayName("[REQ-4][REQ-9] sort=name,asc with instructionsContain uses name order, not rank")
        void explicitNameSortOverridesRelevance() {
            given().queryParam("instructionsContain", "quokka")
                    .queryParam("sort", "name,asc")
                    .get(RECIPES).then()
                    .statusCode(200)
                    .body("content.name", contains("A light quokka", "Z heavy quokka"));
        }

        @Test
        @DisplayName("[REQ-4][REQ-9] sort=createdAt,desc with instructionsContain uses createdAt, not rank")
        void explicitCreatedAtSortOverridesRelevance() {
            given().queryParam("instructionsContain", "quokka")
                    .queryParam("sort", "createdAt,desc")
                    .get(RECIPES).then()
                    .statusCode(200)
                    .body("content.name", contains("A light quokka", "Z heavy quokka"));
        }

        @Test
        @DisplayName("[REQ-4][REQ-9] sort=servings,desc still applies when full-text filter is active")
        void explicitServingsSortWithFullTextFilter() {
            deleteAllThroughApi();
            seed(aRecipe()
                    .withName("Small oven dish")
                    .withServings(2)
                    .withInstructions("Bake in the oven until golden."));
            seed(aRecipe()
                    .withName("Large oven dish")
                    .withServings(6)
                    .withInstructions("Roast in the oven until crisp."));

            given().queryParam("instructionsContain", "oven")
                    .queryParam("sort", "servings,desc")
                    .get(RECIPES).then()
                    .statusCode(200)
                    .body("content.name", contains("Large oven dish", "Small oven dish"))
                    .body("content.servings", contains(6, 2));
        }
    }

    @Nested
    @DisplayName("full-text with structural filters")
    class FullTextWithStructuralFilters {

        @Test
        @DisplayName("[REQ-5][REQ-9] vegetarian=true narrows an instruction search to vegetarian matches only")
        void vegetarianNarrowsFullTextResults() {
            given().queryParam("vegetarian", true)
                    .queryParam("instructionsContain", "oven")
                    .get(RECIPES).then()
                    .statusCode(200)
                    .body("totalElements", equalTo(1))
                    .body("content[0].name", equalTo("Potato gratin"))
                    .body("content[0].vegetarian", equalTo(true));
        }

        @Test
        @DisplayName("[REQ-8][REQ-9] excludeIngredients removes full-text matches that contain the ingredient")
        void exclusionNarrowsFullTextResults() {
            Response response = given().queryParam("excludeIngredients", "salmon")
                    .queryParam("instructionsContain", "oven")
                    .get(RECIPES);

            response.then()
                    .statusCode(200)
                    .body("totalElements", equalTo(1))
                    .body("content[0].name", equalTo("Potato gratin"));

            List<List<String>> ingredients = response.path("content.ingredients");
            assertThat(ingredients).noneMatch(list -> list.contains("salmon"));
        }

        @Test
        @DisplayName("[REQ-6][REQ-7][REQ-9] servings and includeIngredients compose with instruction search")
        void servingsAndIncludeComposeWithFullText() {
            Response response = given().queryParam("servings", 4)
                    .queryParam("includeIngredients", "potatoes")
                    .queryParam("instructionsContain", "oven")
                    .get(RECIPES);

            response.then()
                    .statusCode(200)
                    .body("totalElements", equalTo(2))
                    .body("content.name", contains("Potato gratin", "Salmon traybake"));

            List<List<String>> ingredients = response.path("content.ingredients");
            assertThat(ingredients).allMatch(list -> list.contains("potatoes"));
            List<String> instructions = response.path("content.instructions");
            assertThat(instructions).allMatch(text -> text.toLowerCase().contains("oven"));
        }

        @Test
        @DisplayName("[REQ-10] all structural filters plus instructionsContain return the objective recipe")
        void allStructuralFiltersWithFullText() {
            RecipeTestBuilder gratin = RecipeFixtures.potatoGratin();

            given().queryParam("vegetarian", true)
                    .queryParam("servings", 4)
                    .queryParam("includeIngredients", "potatoes")
                    .queryParam("excludeIngredients", "salmon")
                    .queryParam("instructionsContain", "oven")
                    .get(RECIPES).then()
                    .statusCode(200)
                    .body("totalElements", equalTo(1))
                    .body("content[0].name", equalTo(gratin.name()))
                    .body("content[0].instructions", equalTo(gratin.instructions()));
        }

        @Test
        @DisplayName("[REQ-10] all structural filters plus instructionsContain and explicit sort still return the objective recipe")
        void allStructuralFiltersWithFullTextAndExplicitSort() {
            given().queryParam("vegetarian", true)
                    .queryParam("servings", 4)
                    .queryParam("includeIngredients", "potatoes")
                    .queryParam("excludeIngredients", "salmon")
                    .queryParam("instructionsContain", "oven")
                    .queryParam("sort", "name,asc")
                    .get(RECIPES).then()
                    .statusCode(200)
                    .body("totalElements", equalTo(1))
                    .body("content[0].name", equalTo("Potato gratin"));
        }
    }

    @Nested
    @DisplayName("full-text with pagination")
    class FullTextWithPagination {

        @Test
        @DisplayName("[REQ-4][REQ-9] page envelope reflects the full-text-filtered total, not the table size")
        void paginationEnvelopeReflectsFullTextFilteredTotal() {
            given().queryParam("instructionsContain", "oven")
                    .queryParam("size", 1)
                    .get(RECIPES).then()
                    .statusCode(200)
                    .body("content.size()", equalTo(1))
                    .body("totalElements", equalTo(2))
                    .body("totalPages", equalTo(2));
        }

        @Test
        @DisplayName("[REQ-4][REQ-9][REQ-10] paging through a structural-plus-full-text filter covers every match exactly once")
        void pagingThroughCombinedFiltersIsComplete() {
            List<String> allNames = new java.util.ArrayList<>();
            for (int page = 0; page < 2; page++) {
                List<String> names = given().queryParam("vegetarian", true)
                        .queryParam("instructionsContain", "stove")
                        .queryParam("size", 1)
                        .queryParam("page", page)
                        .get(RECIPES)
                        .then().statusCode(200)
                        .extract().path("content.name");
                allNames.addAll(names);
            }

            assertThat(allNames)
                    .hasSize(2)
                    .doesNotHaveDuplicates()
                    .containsExactlyInAnyOrder("Mushroom risotto", "Vegetable soup");
        }
    }

    /** Clears catalogue through the API — used when deterministic sort fixtures are needed. */
    private void deleteAllThroughApi() {
        Response list = given().get(RECIPES + "?size=100");
        if (list.statusCode() != 200) {
            return;
        }
        List<String> ids = list.path("content.id");
        ids.forEach(id -> given().delete(RECIPES + "/" + id));
    }
}
