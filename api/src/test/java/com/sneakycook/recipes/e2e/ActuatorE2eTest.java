package com.sneakycook.recipes.e2e;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

/** Operational endpoints exposed for orchestrator probes (spec §7). */
class ActuatorE2eTest extends E2eTestBase {

    @Test
    @DisplayName("[REQ-12] GET /actuator/health → 200 with UP status")
    void healthEndpointIsUp() {
        given().get("/actuator/health").then()
                .statusCode(200)
                .body("status", equalTo("UP"));
    }

    @Test
    @DisplayName("[REQ-12] GET /actuator/health/liveness → 200")
    void livenessProbeIsAvailable() {
        given().get("/actuator/health/liveness").then()
                .statusCode(200)
                .body("status", equalTo("UP"));
    }

    @Test
    @DisplayName("[REQ-12] GET /actuator/health/readiness → 200 when database is reachable")
    void readinessProbeIsAvailable() {
        given().get("/actuator/health/readiness").then()
                .statusCode(200)
                .body("status", equalTo("UP"));
    }

    @Test
    @DisplayName("[REQ-12] GET /actuator/metrics → 200 with metric names")
    void metricsEndpointIsExposed() {
        given().get("/actuator/metrics").then()
                .statusCode(200)
                .body("names", notNullValue());
    }
}
