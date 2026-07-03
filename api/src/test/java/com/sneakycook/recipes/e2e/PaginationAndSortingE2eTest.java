package com.sneakycook.recipes.e2e;

import com.sneakycook.recipes.testsupport.RecipeFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;

class PaginationAndSortingE2eTest extends E2eTestBase {

    @BeforeEach
    void seedAllFiveRecipes() {
        seedAll(RecipeFixtures.allFive());
    }

    @Test
    @DisplayName("[REQ-4] size=2 over 5 recipes: first page has 2 items and reports totalElements=5, totalPages=3")
    void firstPageReportsCorrectEnvelopeNumbers() {
        given().get(RECIPES + "?size=2").then()
                .statusCode(200)
                .body("content.size()", equalTo(2))
                .body("page", equalTo(0))
                .body("size", equalTo(2))
                .body("totalElements", equalTo(5))
                .body("totalPages", equalTo(3));
    }

    @Test
    @DisplayName("[REQ-4] the last page carries only the remaining elements")
    void lastPageCarriesOnlyRemainder() {
        given().get(RECIPES + "?size=2&page=2").then()
                .statusCode(200)
                .body("content.size()", equalTo(1))
                .body("page", equalTo(2));
    }

    @Test
    @DisplayName("[REQ-4] pages are disjoint and together cover all recipes exactly once")
    void pagesAreDisjointAndComplete() {
        List<String> allNames = new java.util.ArrayList<>();
        for (int page = 0; page < 3; page++) {
            List<String> names = given().get(RECIPES + "?size=2&page=" + page)
                    .then().statusCode(200)
                    .extract().path("content.name");
            allNames.addAll(names);
        }

        assertThat(allNames)
                .hasSize(5)
                .doesNotHaveDuplicates()
                .containsExactlyInAnyOrder(
                        "Potato gratin", "Salmon traybake", "Mushroom risotto", "Beef stew", "Vegetable soup");
    }

    @Test
    @DisplayName("[REQ-4] default ordering without sort is createdAt descending: newest recipe first")
    void defaultOrderingIsNewestFirst() {
        // Seeding order in @BeforeEach: gratin, traybake, risotto, stew, soup
        given().get(RECIPES).then()
                .statusCode(200)
                .body("content.name", contains(
                        "Vegetable soup", "Beef stew", "Mushroom risotto", "Salmon traybake", "Potato gratin"));
    }

    @Test
    @DisplayName("[REQ-4][REQ-10] page envelope reflects filtered totals: vegetarian=true&size=2 → totalElements=3, totalPages=2")
    void paginationEnvelopeReflectsFilteredTotals() {
        given().get(RECIPES + "?vegetarian=true&size=2").then()
                .statusCode(200)
                .body("content.size()", equalTo(2))
                .body("totalElements", equalTo(3))
                .body("totalPages", equalTo(2));
    }

    @Test
    @DisplayName("[REQ-4] a page index beyond the last page → 200 with empty content, not an error")
    void pageBeyondLastReturnsEmptyContent() {
        given().get(RECIPES + "?size=2&page=9").then()
                .statusCode(200)
                .body("content.size()", equalTo(0))
                .body("page", equalTo(9))
                .body("totalElements", equalTo(5));
    }

    @Test
    @DisplayName("[REQ-4] sort without a direction (sort=name) violates the contract pattern → 400")
    void sortWithoutDirectionReturns400() {
        given().get(RECIPES + "?sort=name").then()
                .statusCode(400)
                .contentType("application/problem+json")
                .body("status", equalTo(400));
    }

    @Test
    @DisplayName("[REQ-4] sort=name,asc returns recipes in alphabetical order")
    void sortByNameAscending() {
        given().get(RECIPES + "?sort=name,asc").then()
                .statusCode(200)
                .body("content.name", contains(
                        "Beef stew", "Mushroom risotto", "Potato gratin", "Salmon traybake", "Vegetable soup"));
    }

    @Test
    @DisplayName("[REQ-4] sort=servings,desc returns recipes by servings descending")
    void sortByServingsDescending() {
        // Exact expected values: stew=6, gratin/traybake/soup=4, risotto=2.
        // Relative order among the tied 4s is unspecified, so assert servings, not names.
        given().get(RECIPES + "?sort=servings,desc").then()
                .statusCode(200)
                .body("content.servings", contains(6, 4, 4, 4, 2));
    }

    @Test
    @DisplayName("[REQ-4] size above the hard cap of 100 → 400 problem document")
    void sizeAboveCapReturns400() {
        given().get(RECIPES + "?size=101").then()
                .statusCode(400)
                .contentType("application/problem+json")
                .body("status", equalTo(400));
    }

    @Test
    @DisplayName("[REQ-4] negative page index → 400 problem document")
    void negativePageReturns400() {
        given().get(RECIPES + "?page=-1").then()
                .statusCode(400)
                .contentType("application/problem+json")
                .body("status", equalTo(400));
    }

    @Test
    @DisplayName("[REQ-4] sort on a disallowed field → 400 problem document")
    void disallowedSortFieldReturns400() {
        given().get(RECIPES + "?sort=instructions,asc").then()
                .statusCode(400)
                .contentType("application/problem+json")
                .body("status", equalTo(400));
    }
}
