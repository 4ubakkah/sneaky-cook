package com.sneakycook.recipes.e2e;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

/**
 * Fallback error shape [REQ-13, spec §6]: an unexpected server-side failure
 * must speak RFC 7807 like every other error and leak nothing about the
 * internals. Reproduced with a test-only controller that throws; its exception
 * message plays the role of the internals that must never reach a client.
 */
class UnexpectedErrorE2eTest extends E2eTestBase {

    /** Stands in for a stack trace / infrastructure detail a client must never see. */
    private static final String INTERNAL_SECRET = "connection to internal-db-7 lost";

    @TestConfiguration
    static class ThrowingControllerConfig {

        @Bean
        ThrowingController throwingController() {
            return new ThrowingController();
        }
    }

    @RestController
    static class ThrowingController {

        @GetMapping("/test-support/boom")
        String boom() {
            throw new IllegalStateException(INTERNAL_SECRET);
        }
    }

    @Test
    @DisplayName("[REQ-13] unexpected exception → 500 problem document with no internals leaked")
    void unexpectedExceptionReturns500ProblemDocument() {
        given().get("/test-support/boom").then()
                .statusCode(500)
                .contentType("application/problem+json")
                .body("status", equalTo(500))
                .body("title", equalTo("Internal Server Error"))
                .body("detail", equalTo("An unexpected error occurred"))
                .body("instance", equalTo("/test-support/boom"))
                .body(not(containsString(INTERNAL_SECRET)));
    }
}
