package com.sneakycook.recipes.e2e;

import com.sneakycook.recipes.testsupport.RecipeTestBuilder;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static io.restassured.RestAssured.given;

/**
 * Base for E2E tests: real HTTP against a random port, real PostgreSQL via a
 * singleton Testcontainer (spec §7 — no in-memory database anywhere, REQ-12).
 * Tests seed data exclusively through the public API.
 *
 * <p>Authentication seam (spec §13, challenge 8): every test runs as a freshly
 * registered user whose bearer token rides on all requests through
 * RestAssured's global authentication — the wiring changed, test content did
 * not. Per-test users also make recipe data owner-isolated by construction
 * [REQ-18].
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class E2eTestBase {

    protected static final String RECIPES = "/api/v1/recipes";
    protected static final String AUTH = "/api/v1/auth";
    protected static final String TEST_PASSWORD = "correct-horse-battery";

    /** Unique usernames across every E2E class sharing this JVM and database. */
    private static final AtomicLong USER_SEQUENCE = new AtomicLong();

    /** Singleton container shared by all E2E classes; started once per JVM. */
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @LocalServerPort
    private int port;

    @BeforeEach
    void configureRestAssuredAndCleanState() {
        RestAssured.port = port;
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
        switchToNewUser();
        deleteAllRecipesThroughApi();
    }

    protected record TestUser(String username, String password, String token) {
    }

    /**
     * Registers a fresh user through the public API [REQ-17] and returns its
     * bearer token; does NOT change which user subsequent requests run as.
     */
    protected TestUser registerNewUser() {
        String username = "user-" + USER_SEQUENCE.incrementAndGet();
        given().auth().none()
                .contentType(ContentType.JSON)
                .body(Map.of("username", username, "password", TEST_PASSWORD))
                .post(AUTH + "/register")
                .then()
                .statusCode(201);
        String token = given().auth().none()
                .contentType(ContentType.JSON)
                .body(Map.of("username", username, "password", TEST_PASSWORD))
                .post(AUTH + "/login")
                .then()
                .statusCode(200)
                .extract()
                .path("accessToken");
        return new TestUser(username, TEST_PASSWORD, token);
    }

    /** [REQ-18] All subsequent requests run as a brand-new user; returns it. */
    protected TestUser switchToNewUser() {
        TestUser user = registerNewUser();
        RestAssured.authentication = RestAssured.oauth2(user.token());
        return user;
    }

    /** Test isolation through the API itself; a no-op while endpoints are still 501. */
    private void deleteAllRecipesThroughApi() {
        Response list = given().get(RECIPES + "?size=100");
        if (list.statusCode() != 200) {
            return;
        }
        List<String> ids = list.path("content.id");
        ids.forEach(id -> given().delete(RECIPES + "/" + id));
    }

    protected ValidatableResponse postRecipe(Map<String, Object> body) {
        return given()
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .post(RECIPES)
                .then();
    }

    /** Seeds one recipe through the API, asserting creation succeeded (201). */
    protected String seed(RecipeTestBuilder recipe) {
        return postRecipe(recipe.buildRequest())
                .statusCode(201)
                .extract()
                .path("id");
    }

    protected void seedAll(List<RecipeTestBuilder> recipes) {
        recipes.forEach(this::seed);
    }
}
