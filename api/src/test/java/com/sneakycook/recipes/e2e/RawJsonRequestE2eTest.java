package com.sneakycook.recipes.e2e;

import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;

/**
 * Wire-level robustness [REQ-2, REQ-13]: payloads a real client can send but a
 * typed builder cannot express — malformed JSON, wrong field types, explicit
 * nulls, wrong content type. Bodies are raw JSON strings on purpose; do not
 * "clean these up" into maps or DTOs.
 */
@Tag("red")
class RawJsonRequestE2eTest extends E2eTestBase {

    private ValidatableResponse postRawJson(String body) {
        return given()
                .contentType("application/json")
                .body(body)
                .when()
                .post(RECIPES)
                .then();
    }

    @Test
    @DisplayName("[REQ-2] syntactically malformed JSON → 400 problem document")
    void malformedJsonReturns400() {
        postRawJson("{\"name\": \"Broken recipe\", ")
                .statusCode(400)
                .contentType("application/problem+json")
                .body("status", equalTo(400));
    }

    @Test
    @DisplayName("[REQ-2] empty request body → 400 problem document")
    void emptyBodyReturns400() {
        postRawJson("")
                .statusCode(400)
                .contentType("application/problem+json")
                .body("status", equalTo(400));
    }

    @Test
    @DisplayName("[REQ-2] servings as a string ('four') → 400 problem document")
    void stringServingsReturns400() {
        postRawJson("""
                {"name":"Potato gratin","vegetarian":true,"servings":"four",
                 "ingredients":["potatoes"],"instructions":"Bake in the oven."}
                """)
                .statusCode(400)
                .contentType("application/problem+json")
                .body("status", equalTo(400));
    }

    @Test
    @DisplayName("[REQ-2] non-integer servings (4.5) is rejected, not silently truncated → 400")
    void decimalServingsReturns400() {
        postRawJson("""
                {"name":"Potato gratin","vegetarian":true,"servings":4.5,
                 "ingredients":["potatoes"],"instructions":"Bake in the oven."}
                """)
                .statusCode(400)
                .contentType("application/problem+json")
                .body("status", equalTo(400));
    }

    @Test
    @DisplayName("[REQ-2] vegetarian as a string ('definitely') → 400 problem document")
    void stringVegetarianReturns400() {
        postRawJson("""
                {"name":"Potato gratin","vegetarian":"definitely","servings":4,
                 "ingredients":["potatoes"],"instructions":"Bake in the oven."}
                """)
                .statusCode(400)
                .contentType("application/problem+json")
                .body("status", equalTo(400));
    }

    @Test
    @DisplayName("[REQ-2] ingredients as a scalar instead of an array → 400 problem document")
    void scalarIngredientsReturns400() {
        postRawJson("""
                {"name":"Potato gratin","vegetarian":true,"servings":4,
                 "ingredients":"potatoes","instructions":"Bake in the oven."}
                """)
                .statusCode(400)
                .contentType("application/problem+json")
                .body("status", equalTo(400));
    }

    @Test
    @DisplayName("[REQ-2] explicit null for a required field ('name') → 400 with a 'name' field error")
    void explicitNullNameReturns400() {
        postRawJson("""
                {"name":null,"vegetarian":true,"servings":4,
                 "ingredients":["potatoes"],"instructions":"Bake in the oven."}
                """)
                .statusCode(400)
                .contentType("application/problem+json")
                .body("status", equalTo(400))
                .body("errors.field", hasItem("name"));
    }

    @Test
    @DisplayName("[REQ-2] unknown extra fields are ignored, the recipe is created → 201")
    void unknownExtraFieldIsIgnored() {
        postRawJson("""
                {"name":"Potato gratin","vegetarian":true,"servings":4,
                 "ingredients":["potatoes"],"instructions":"Bake in the oven.",
                 "rating":5,"author":"someone"}
                """)
                .statusCode(201)
                .body("name", equalTo("Potato gratin"));
    }

    @Test
    @DisplayName("[REQ-2] wrong Content-Type (text/plain) → 415")
    void wrongContentTypeReturns415() {
        given().contentType("text/plain")
                .body("{\"name\":\"Potato gratin\"}")
                .post(RECIPES)
                .then()
                .statusCode(415);
    }
}
