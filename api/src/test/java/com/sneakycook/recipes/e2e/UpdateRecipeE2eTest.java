package com.sneakycook.recipes.e2e;

import com.sneakycook.recipes.testsupport.RecipeTestBuilder;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.sneakycook.recipes.testsupport.RecipeFixtures.beefStew;
import static com.sneakycook.recipes.testsupport.RecipeFixtures.potatoGratin;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;

class UpdateRecipeE2eTest extends E2eTestBase {

    private static final String UNKNOWN_ID = "6f1e2d3c-0000-0000-0000-000000000000";

    private static RecipeTestBuilder leekGratinUpdate() {
        return potatoGratin()
                .withName("Potato and leek gratin")
                .withServings(6)
                .withIngredients("potatoes", "leeks", "cream", "cheese")
                .withInstructions("Slice the potatoes and leeks. Layer with cream and cheese. "
                        + "Bake in the oven at 180°C for 50 minutes.");
    }

    @Test
    @DisplayName("PUT /recipes/{id} → 200 with the updated payload")
    void updateReturns200WithUpdatedPayload() {
        String id = seed(potatoGratin());
        RecipeTestBuilder updated = leekGratinUpdate();

        given().contentType(ContentType.JSON).body(updated.buildRequest())
                .put(RECIPES + "/" + id).then()
                .statusCode(200)
                .contentType("application/json")
                .body("id", equalTo(id))
                .body("name", equalTo(updated.name()))
                .body("vegetarian", equalTo(updated.vegetarian()))
                .body("servings", equalTo(updated.servings()))
                .body("ingredients", contains(updated.ingredients().toArray()))
                .body("instructions", equalTo(updated.instructions()));
    }

    @Test
    @DisplayName("PUT is persistent: a subsequent GET returns the updated values")
    void updateIsPersistedAndRetrievable() {
        String id = seed(potatoGratin());
        RecipeTestBuilder updated = leekGratinUpdate();

        given().contentType(ContentType.JSON).body(updated.buildRequest())
                .put(RECIPES + "/" + id).then()
                .statusCode(200);

        given().get(RECIPES + "/" + id).then()
                .statusCode(200)
                .body("name", equalTo(updated.name()))
                .body("vegetarian", equalTo(updated.vegetarian()))
                .body("servings", equalTo(updated.servings()))
                .body("ingredients", contains(updated.ingredients().toArray()))
                .body("instructions", equalTo(updated.instructions()));
    }

    @Test
    @DisplayName("PUT /recipes/{unknown} → 404 problem document naming the id")
    void updateUnknownIdReturns404() {
        given().contentType(ContentType.JSON).body(potatoGratin().buildRequest())
                .put(RECIPES + "/" + UNKNOWN_ID).then()
                .statusCode(404)
                .contentType("application/problem+json")
                .body("status", equalTo(404))
                .body("detail", containsString(UNKNOWN_ID));
    }

    @Test
    @DisplayName("PUT with zero servings → 400 problem document with a 'servings' field error")
    void updateWithZeroServingsReturns400() {
        String id = seed(potatoGratin());

        given().contentType(ContentType.JSON)
                .body(potatoGratin().withServings(0).buildRequest())
                .put(RECIPES + "/" + id).then()
                .statusCode(400)
                .contentType("application/problem+json")
                .body("status", equalTo(400))
                .body("errors.field", hasItem("servings"));
    }

    @Test
    @DisplayName("PUT /recipes/{malformed-uuid} → 400 problem document")
    void updateMalformedIdReturns400() {
        given().contentType(ContentType.JSON).body(potatoGratin().buildRequest())
                .put(RECIPES + "/not-a-uuid").then()
                .statusCode(400)
                .contentType("application/problem+json")
                .body("status", equalTo(400));
    }

    @Test
    @Tag("red") // needs the filter engine (build-order step 5)
    @DisplayName("[REQ-5] updates are visible to filters: a recipe turned non-vegetarian leaves vegetarian=true results")
    void updatedRecipeIsVisibleToFiltersWithNewValues() {
        String id = seed(potatoGratin());

        given().contentType(ContentType.JSON)
                .body(potatoGratin().withVegetarian(false).buildRequest())
                .put(RECIPES + "/" + id).then()
                .statusCode(200);

        given().get(RECIPES + "?vegetarian=true").then()
                .statusCode(200)
                .body("totalElements", equalTo(0));

        given().get(RECIPES + "?vegetarian=false").then()
                .statusCode(200)
                .body("totalElements", equalTo(1))
                .body("content[0].id", equalTo(id));
    }

    @Test
    @DisplayName("a rejected PUT leaves the stored recipe unchanged")
    void rejectedUpdateLeavesRecipeUnchanged() {
        RecipeTestBuilder original = potatoGratin();
        String id = seed(original);

        given().contentType(ContentType.JSON)
                .body(potatoGratin().withServings(0).buildRequest())
                .put(RECIPES + "/" + id).then()
                .statusCode(400);

        given().get(RECIPES + "/" + id).then()
                .statusCode(200)
                .body("name", equalTo(original.name()))
                .body("servings", equalTo(original.servings()))
                .body("ingredients", contains(original.ingredients().toArray()));
    }

    @Test
    @DisplayName("PUT does not change createdAt: the creation timestamp survives a full update")
    void updateDoesNotChangeCreatedAt() {
        var created = postRecipe(potatoGratin().buildRequest())
                .statusCode(201)
                .extract();
        String id = created.path("id");
        String createdAt = created.path("createdAt");

        given().contentType(ContentType.JSON).body(leekGratinUpdate().buildRequest())
                .put(RECIPES + "/" + id).then()
                .statusCode(200);

        given().get(RECIPES + "/" + id).then()
                .statusCode(200)
                .body("createdAt", equalTo(createdAt));
    }

    @Test
    @DisplayName("an 'id' smuggled into the PUT body is ignored: the resource keeps its path id")
    void idInBodyDoesNotChangeResourceId() {
        String id = seed(potatoGratin());
        String smuggledId = "1c9c9a3e-5b1f-4c47-9a2d-7e8f13d24a6b";

        Map<String, Object> request = leekGratinUpdate().buildRequest();
        request.put("id", smuggledId);

        given().contentType(ContentType.JSON).body(request)
                .put(RECIPES + "/" + id).then()
                .statusCode(200)
                .body("id", equalTo(id));

        given().get(RECIPES + "/" + smuggledId).then().statusCode(404);
        given().get(RECIPES + "/" + id).then().statusCode(200);
    }

    @Test
    @DisplayName("[REQ-7] PUT normalizes ingredient names to lower case, same as create")
    void updateNormalizesIngredientsToLowercase() {
        String id = seed(potatoGratin());

        given().contentType(ContentType.JSON)
                .body(potatoGratin().withIngredients("Leeks", "CREAM").buildRequest())
                .put(RECIPES + "/" + id).then()
                .statusCode(200)
                .body("ingredients", contains("leeks", "cream"));
    }

    @Test
    @DisplayName("PUT modifies only the targeted recipe, other recipes are untouched")
    void updateLeavesOtherRecipesUntouched() {
        RecipeTestBuilder stew = beefStew();
        String gratinId = seed(potatoGratin());
        String stewId = seed(stew);

        given().contentType(ContentType.JSON).body(leekGratinUpdate().buildRequest())
                .put(RECIPES + "/" + gratinId).then()
                .statusCode(200);

        given().get(RECIPES + "/" + stewId).then()
                .statusCode(200)
                .body("name", equalTo(stew.name()))
                .body("servings", equalTo(stew.servings()))
                .body("ingredients", contains(stew.ingredients().toArray()));
    }

    @Test
    @DisplayName("PUT is idempotent: repeating the identical update succeeds and leaves the same state")
    void putIsIdempotent() {
        String id = seed(potatoGratin());
        RecipeTestBuilder updated = leekGratinUpdate();

        given().contentType(ContentType.JSON).body(updated.buildRequest())
                .put(RECIPES + "/" + id).then().statusCode(200);
        given().contentType(ContentType.JSON).body(updated.buildRequest())
                .put(RECIPES + "/" + id).then().statusCode(200);

        given().get(RECIPES + "/" + id).then()
                .statusCode(200)
                .body("name", equalTo(updated.name()))
                .body("servings", equalTo(updated.servings()))
                .body("ingredients", contains(updated.ingredients().toArray()));
    }

    @Test
    @Tag("red") // needs the filter engine (build-order step 5)
    @DisplayName("[REQ-7][REQ-8] updated ingredients are visible to ingredient filters")
    void updatedIngredientsAreVisibleToIngredientFilters() {
        String id = seed(potatoGratin());

        given().contentType(ContentType.JSON)
                .body(potatoGratin().withIngredients("salmon", "potatoes").buildRequest())
                .put(RECIPES + "/" + id).then()
                .statusCode(200);

        given().get(RECIPES + "?includeIngredients=salmon").then()
                .statusCode(200)
                .body("totalElements", equalTo(1))
                .body("content[0].id", equalTo(id));

        given().get(RECIPES + "?excludeIngredients=salmon").then()
                .statusCode(200)
                .body("totalElements", equalTo(0));
    }

    @Test
    @Tag("red") // needs full-text search (build-order step 6)
    @DisplayName("[REQ-9] updated instructions are visible to text search")
    void updatedInstructionsAreVisibleToTextSearch() {
        String id = seed(potatoGratin()); // instructions mention the oven

        given().get(RECIPES + "?instructionsContain=oven").then()
                .statusCode(200)
                .body("totalElements", equalTo(1));

        given().contentType(ContentType.JSON)
                .body(potatoGratin()
                        .withInstructions("Simmer everything gently on the stove until soft and creamy.")
                        .buildRequest())
                .put(RECIPES + "/" + id).then()
                .statusCode(200);

        given().get(RECIPES + "?instructionsContain=oven").then()
                .statusCode(200)
                .body("totalElements", equalTo(0));

        given().get(RECIPES + "?instructionsContain=stove").then()
                .statusCode(200)
                .body("totalElements", equalTo(1))
                .body("content[0].id", equalTo(id));
    }
}
