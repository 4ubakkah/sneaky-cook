package com.sneakycook.recipes.infrastructure;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.Instant;
import java.util.UUID;

/**
 * Base for specification-tier tests: the real schema (Flyway) on a real
 * PostgreSQL via a singleton Testcontainer — never an in-memory database
 * (REQ-12, spec §7), because the filter SQL under test is Postgres SQL.
 *
 * <p>Seeds the two fixture users before each test so recipe rows can satisfy
 * the {@code owner_id} foreign key [REQ-18]; the @DataJpaTest rollback wipes
 * them again afterwards.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({RecipeRepositoryAdapter.class, RecipeEntityMapperImpl.class})
abstract class PostgresDataJpaTestBase {

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

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void seedFixtureOwners() {
        entityManager.persist(fixtureUser(DomainRecipes.OWNER, "fixture-owner"));
        entityManager.persist(fixtureUser(DomainRecipes.OTHER_OWNER, "fixture-other-owner"));
        entityManager.flush();
    }

    private static UserEntity fixtureUser(UUID id, String username) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setUsername(username);
        user.setPasswordHash("$2a$10$fixture-hash-never-verified-in-this-tier");
        user.setCreatedAt(Instant.parse("2026-07-03T11:00:00Z"));
        return user;
    }
}
