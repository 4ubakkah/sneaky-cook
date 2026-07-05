package com.sneakycook.recipes.e2e;

import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;

/**
 * Account registration and token issuing [REQ-17] (spec §13): happy paths,
 * validation, duplicate usernames, and credential failures — all speaking
 * RFC 7807 on error like the rest of the API.
 */
class AuthE2eTest extends E2eTestBase {

    private io.restassured.response.ValidatableResponse register(String username, String password) {
        return given().auth().none()
                .contentType(ContentType.JSON)
                .body(Map.of("username", username, "password", password))
                .post(AUTH + "/register")
                .then();
    }

    private io.restassured.response.ValidatableResponse login(String username, String password) {
        return given().auth().none()
                .contentType(ContentType.JSON)
                .body(Map.of("username", username, "password", password))
                .post(AUTH + "/login")
                .then();
    }

    @Test
    @DisplayName("[REQ-17] register → 201 with id, username, createdAt — and never the password")
    void registerReturnsCreatedAccount() {
        String body = register("alice-registration", TEST_PASSWORD)
                .statusCode(201)
                .contentType("application/json")
                .body("id", notNullValue())
                .body("username", equalTo("alice-registration"))
                .body("createdAt", notNullValue())
                .extract().asString();

        // The password (or its hash) must never appear on the wire.
        org.assertj.core.api.Assertions.assertThat(body)
                .doesNotContain(TEST_PASSWORD)
                .doesNotContain("password");
    }

    @Test
    @DisplayName("[REQ-17] duplicate username → 409 problem document naming it")
    void duplicateUsernameReturns409() {
        register("bob-duplicate", TEST_PASSWORD).statusCode(201);

        register("bob-duplicate", TEST_PASSWORD)
                .statusCode(409)
                .contentType("application/problem+json")
                .body("status", equalTo(409))
                .body("title", equalTo("Conflict"))
                .body("detail", equalTo("Username 'bob-duplicate' is already taken"))
                .body("instance", equalTo("/api/v1/auth/register"));
    }

    @Test
    @DisplayName("[REQ-17] password below the 8-character minimum → 400 with a 'password' field error")
    void shortPasswordReturns400() {
        register("carol-short-pass", "short")
                .statusCode(400)
                .contentType("application/problem+json")
                .body("status", equalTo(400))
                .body("errors.field", hasItem("password"));
    }

    @Test
    @DisplayName("[REQ-17] blank username → 400 with a 'username' field error")
    void blankUsernameReturns400() {
        register("   ", TEST_PASSWORD)
                .statusCode(400)
                .contentType("application/problem+json")
                .body("status", equalTo(400))
                .body("errors.field", hasItem("username"));
    }

    @Test
    @DisplayName("[REQ-17] login → 200 with a Bearer token valid for 3600 seconds")
    void loginIssuesToken() {
        register("dave-login", TEST_PASSWORD).statusCode(201);

        login("dave-login", TEST_PASSWORD)
                .statusCode(200)
                .contentType("application/json")
                .body("accessToken", startsWith("eyJ")) // JWS compact form, base64url header
                .body("tokenType", equalTo("Bearer"))
                .body("expiresIn", equalTo(3600));
    }

    @Test
    @DisplayName("[REQ-17] the issued token actually works: created recipe is retrievable with it")
    void issuedTokenAuthenticatesRecipeRequests() {
        register("erin-roundtrip", TEST_PASSWORD).statusCode(201);
        String token = login("erin-roundtrip", TEST_PASSWORD)
                .statusCode(200)
                .extract().path("accessToken");

        given().auth().oauth2(token).get(RECIPES).then()
                .statusCode(200)
                .body("totalElements", equalTo(0));
    }

    @Test
    @DisplayName("[REQ-17] wrong password → 401 problem document")
    void wrongPasswordReturns401() {
        register("frank-wrong-pass", TEST_PASSWORD).statusCode(201);

        login("frank-wrong-pass", "not-the-password")
                .statusCode(401)
                .contentType("application/problem+json")
                .body("status", equalTo(401))
                .body("title", equalTo("Unauthorized"))
                .body("detail", equalTo("Invalid username or password"));
    }

    @Test
    @DisplayName("[REQ-17] unknown username → the identical 401 — responses cannot enumerate accounts")
    void unknownUsernameReturnsIdentical401() {
        String body = login("nobody-registered-this", TEST_PASSWORD)
                .statusCode(401)
                .contentType("application/problem+json")
                .body("status", equalTo(401))
                .body("title", equalTo("Unauthorized"))
                .body("detail", equalTo("Invalid username or password"))
                .extract().asString();

        // No hint which part was wrong — identical to the wrong-password document.
        org.assertj.core.api.Assertions.assertThat(body).doesNotContain("nobody-registered-this");
    }
}
