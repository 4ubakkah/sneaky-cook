package com.sneakycook.recipes.e2e;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sneakycook.recipes.api.AuthProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * [REQ-17] Every recipe endpoint rejects unauthenticated callers with an
 * RFC 7807 401 — Spring Security's default empty 401 would break the error
 * contract — while health probes and the API docs stay open (spec §13).
 */
class UnauthenticatedAccessE2eTest extends E2eTestBase {

    @Autowired
    private AuthProperties authProperties;

    @Test
    @DisplayName("[REQ-17] no token → 401 problem document, not an empty response")
    void missingTokenReturns401Problem() {
        given().auth().none().get(RECIPES).then()
                .statusCode(401)
                .contentType("application/problem+json")
                .body("status", equalTo(401))
                .body("title", equalTo("Unauthorized"))
                .body("detail", equalTo("Authentication required — provide a bearer token"))
                .body("instance", equalTo("/api/v1/recipes"));
    }

    @Test
    @DisplayName("[REQ-17] writes are protected too: unauthenticated POST → 401 problem document")
    void missingTokenOnWriteReturns401Problem() {
        given().auth().none()
                .contentType("application/json")
                .body("{\"name\":\"Sneaky recipe\"}")
                .post(RECIPES)
                .then()
                .statusCode(401)
                .contentType("application/problem+json")
                .body("status", equalTo(401));
    }

    @Test
    @DisplayName("[REQ-17] garbage token → 401 problem document")
    void garbageTokenReturns401Problem() {
        given().auth().oauth2("not-a-jwt-at-all").get(RECIPES).then()
                .statusCode(401)
                .contentType("application/problem+json")
                .body("status", equalTo(401))
                .body("title", equalTo("Unauthorized"));
    }

    @Test
    @DisplayName("[REQ-17] expired token → 401 problem document")
    void expiredTokenReturns401Problem() throws Exception {
        given().auth().oauth2(expiredToken()).get(RECIPES).then()
                .statusCode(401)
                .contentType("application/problem+json")
                .body("status", equalTo(401))
                .body("title", equalTo("Unauthorized"));
    }

    @Test
    @DisplayName("[REQ-13] health probes stay open — orchestrators carry no tokens")
    void healthIsOpenWithoutToken() {
        given().auth().none().get("/actuator/health").then()
                .statusCode(200)
                .body("status", equalTo("UP"));
        given().auth().none().get("/actuator/health/liveness").then().statusCode(200);
        given().auth().none().get("/actuator/health/readiness").then().statusCode(200);
    }

    @Test
    @DisplayName("[REQ-11] the API docs stay open — the contract is public, the data is not")
    void apiDocsAreOpenWithoutToken() {
        given().auth().none().get("/openapi/recipe-api.yaml").then().statusCode(200);
        given().auth().none().get("/swagger-ui.html").then().statusCode(200);
    }

    /** Signed with the real configured secret, expired an hour ago — only the timestamps are wrong. */
    private String expiredToken() throws Exception {
        Instant now = Instant.now();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader(JWSAlgorithm.HS256),
                new JWTClaimsSet.Builder()
                        .subject(UUID.randomUUID().toString())
                        .issueTime(Date.from(now.minusSeconds(7200)))
                        .expirationTime(Date.from(now.minusSeconds(3600)))
                        .build());
        jwt.sign(new MACSigner(authProperties.jwtSecret().getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }
}
