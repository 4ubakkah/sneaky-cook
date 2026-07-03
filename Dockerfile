# Multi-stage build: Maven compile on JDK 21, runtime on JRE 21 as non-root (spec §7).
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
COPY domain/pom.xml domain/
COPY application/pom.xml application/
COPY infrastructure/pom.xml infrastructure/
COPY api/pom.xml api/

COPY domain/src domain/src
COPY application/src application/src
COPY infrastructure/src infrastructure/src
COPY api/src api/src

RUN mvn -q -pl api -am package -DskipTests

FROM eclipse-temurin:21-jre AS runtime
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app \
    && useradd --system --gid app app

WORKDIR /app
COPY --from=build /workspace/api/target/recipe-api-*.jar app.jar
RUN chown app:app app.jar

USER app
EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=5s --retries=5 --start-period=30s \
  CMD curl -sf http://localhost:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
