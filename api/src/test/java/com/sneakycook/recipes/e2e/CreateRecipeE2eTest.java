package com.sneakycook.recipes.e2e;

import com.sneakycook.recipes.testsupport.RecipeTestBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static com.sneakycook.recipes.testsupport.RecipeFixtures.potatoGratin;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;

class CreateRecipeE2eTest extends E2eTestBase {

    @Test
    @DisplayName("[REQ-1][REQ-2] POST /recipes → 201 with the full recipe payload")
    void createReturns201WithFullPayload() {
        RecipeTestBuilder gratin = potatoGratin();

        postRecipe(gratin.buildRequest())
                .statusCode(201)
                .contentType("application/json")
                .body("id", notNullValue())
                .body("name", equalTo(gratin.name()))
                .body("vegetarian", equalTo(gratin.vegetarian()))
                .body("servings", equalTo(gratin.servings()))
                .body("ingredients", contains(gratin.ingredients().toArray()))
                .body("instructions", equalTo(gratin.instructions()))
                .body("createdAt", notNullValue());
    }

    @Test
    @DisplayName("[REQ-2] 201 response carries a Location header pointing at the created recipe")
    void locationHeaderPointsAtCreatedRecipe() {
        var response = postRecipe(potatoGratin().buildRequest())
                .statusCode(201)
                .header("Location", containsString("/api/v1/recipes/"))
                .extract();

        String id = response.path("id");
        String location = response.header("Location");

        given().get(location).then()
                .statusCode(200)
                .body("id", equalTo(id));
    }

    @Test
    @DisplayName("[REQ-2] created recipe is retrievable with identical field values")
    void createdRecipeIsRetrievableWithIdenticalValues() {
        RecipeTestBuilder gratin = potatoGratin();
        String id = seed(gratin);

        given().get(RECIPES + "/" + id).then()
                .statusCode(200)
                .body("id", equalTo(id))
                .body("name", equalTo(gratin.name()))
                .body("vegetarian", equalTo(gratin.vegetarian()))
                .body("servings", equalTo(gratin.servings()))
                .body("ingredients", contains(gratin.ingredients().toArray()))
                .body("instructions", equalTo(gratin.instructions()))
                .body("createdAt", notNullValue());
    }

    @Test
    @DisplayName("[REQ-2] blank name → 400 problem document with a 'name' field error")
    void blankNameReturns400() {
        postRecipe(potatoGratin().withName("").buildRequest())
                .statusCode(400)
                .contentType("application/problem+json")
                .body("status", equalTo(400))
                .body("title", equalTo("Bad Request"))
                .body("instance", equalTo("/api/v1/recipes"))
                .body("errors.field", hasItem("name"));
    }

    @Test
    @DisplayName("[REQ-2] zero servings violates the minimum of 1 → 400 with a 'servings' field error")
    void zeroServingsReturns400() {
        postRecipe(potatoGratin().withServings(0).buildRequest())
                .statusCode(400)
                .contentType("application/problem+json")
                .body("status", equalTo(400))
                .body("errors.field", hasItem("servings"));
    }

    @Test
    @DisplayName("[REQ-2] empty ingredient list violates minItems of 1 → 400 with an 'ingredients' field error")
    void emptyIngredientsReturns400() {
        postRecipe(potatoGratin().withNoIngredients().buildRequest())
                .statusCode(400)
                .contentType("application/problem+json")
                .body("status", equalTo(400))
                .body("errors.field", hasItem("ingredients"));
    }

    @Test
    @DisplayName("[REQ-2] blank instructions → 400 with an 'instructions' field error")
    void blankInstructionsReturns400() {
        postRecipe(potatoGratin().withInstructions("").buildRequest())
                .statusCode(400)
                .contentType("application/problem+json")
                .body("status", equalTo(400))
                .body("errors.field", hasItem("instructions"));
    }

    @ParameterizedTest(name = "[REQ-2] missing required field ''{0}'' → 400 with a field error for it")
    @ValueSource(strings = {"name", "vegetarian", "servings", "ingredients", "instructions"})
    void missingRequiredFieldReturns400(String missingField) {
        Map<String, Object> request = potatoGratin().buildRequest();
        request.remove(missingField);

        postRecipe(request)
                .statusCode(400)
                .contentType("application/problem+json")
                .body("status", equalTo(400))
                .body("errors.field", hasItem(missingField));
    }

    @Test
    @DisplayName("[REQ-2] name of exactly 200 characters (the maximum) is accepted → 201")
    void nameAtMaxLengthIsAccepted() {
        postRecipe(potatoGratin().withName("N".repeat(200)).buildRequest())
                .statusCode(201)
                .body("name", equalTo("N".repeat(200)));
    }

    @Test
    @DisplayName("[REQ-2] name of 201 characters exceeds the maximum → 400 with a 'name' field error")
    void nameOverMaxLengthReturns400() {
        postRecipe(potatoGratin().withName("N".repeat(201)).buildRequest())
                .statusCode(400)
                .contentType("application/problem+json")
                .body("errors.field", hasItem("name"));
    }

    @Test
    @DisplayName("[REQ-2] ingredient name of 101 characters exceeds the maximum of 100 → 400")
    void ingredientOverMaxLengthReturns400() {
        postRecipe(potatoGratin().withIngredients("I".repeat(101)).buildRequest())
                .statusCode(400)
                .contentType("application/problem+json")
                .body("errors.field", hasItem(containsStringIgnoringCase("ingredients")));
    }

    @Test
    @DisplayName("[REQ-2] servings=1 (the minimum) is accepted → 201")
    void servingsAtMinimumIsAccepted() {
        postRecipe(potatoGratin().withServings(1).buildRequest())
                .statusCode(201)
                .body("servings", equalTo(1));
    }

    @Test
    @Tag("red") // needs the list endpoint (build-order step 4)
    @DisplayName("[REQ-2] duplicate recipe names are allowed: two recipes may share a name")
    void duplicateNamesAreAllowed() {
        String firstId = seed(potatoGratin());
        String secondId = seed(potatoGratin());

        org.assertj.core.api.Assertions.assertThat(firstId).isNotEqualTo(secondId);

        given().get(RECIPES).then()
                .statusCode(200)
                .body("totalElements", equalTo(2));
    }

    @Test
    @DisplayName("[REQ-2] createdAt is stable: GET returns the same timestamp the POST response reported")
    void createdAtIsStableAcrossReads() {
        var created = postRecipe(potatoGratin().buildRequest())
                .statusCode(201)
                .extract();

        String id = created.path("id");
        String createdAt = created.path("createdAt");

        given().get(RECIPES + "/" + id).then()
                .statusCode(200)
                .body("createdAt", equalTo(createdAt));
    }

    @Test
    @DisplayName("[REQ-7] ingredient names are normalized to lower case on write")
    void ingredientsAreLowercasedOnWrite() {
        postRecipe(potatoGratin().withIngredients("Potatoes", "CREAM", "Cheese", "garlic").buildRequest())
                .statusCode(201)
                .body("ingredients", contains("potatoes", "cream", "cheese", "garlic"));
    }

    @Test
    @DisplayName("[REQ-2] whitespace-only name → 400 with a 'name' field error")
    void whitespaceOnlyNameReturns400() {
        postRecipe(potatoGratin().withName("   ").buildRequest())
                .statusCode(400)
                .contentType("application/problem+json")
                .body("errors.field", hasItem("name"));
    }

    @Test
    @Tag("red") // needs the list endpoint (build-order step 4)
    @DisplayName("[REQ-2] a rejected POST persists nothing: the catalogue stays empty")
    void rejectedCreateLeavesCatalogueEmpty() {
        postRecipe(potatoGratin().withServings(0).buildRequest())
                .statusCode(400);

        given().get(RECIPES).then()
                .statusCode(200)
                .body("totalElements", equalTo(0));
    }
}
