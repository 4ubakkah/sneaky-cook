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

import static io.restassured.RestAssured.given;

/**
 * Base for E2E tests: real HTTP against a random port, real PostgreSQL via a
 * singleton Testcontainer (spec §7 — no in-memory database anywhere, REQ-12).
 * Tests seed data exclusively through the public API.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class E2eTestBase {

    protected static final String RECIPES = "/api/v1/recipes";

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
        deleteAllRecipesThroughApi();
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
