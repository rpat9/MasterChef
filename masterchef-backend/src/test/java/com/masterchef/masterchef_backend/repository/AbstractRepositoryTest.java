package com.masterchef.masterchef_backend.repository;

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Shared base class for all @DataJpaTest repository tests.
 *
 * A single PostgreSQL container is started once for the entire test suite
 * (static field) and reused across all subclasses. This keeps the suite fast
 * while still running against a real PostgreSQL instance — necessary because
 * the schema uses PostgreSQL-specific types (JSONB, TEXT[]) that H2 cannot
 * handle.
 *
 * Flyway runs automatically inside the container so every test starts with
 * the real production schema.
 */
@DataJpaTest
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public abstract class AbstractRepositoryTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("masterchef_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void overrideDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        // Let Flyway create the schema inside the container
        registry.add("spring.flyway.enabled", () -> "true");
        // Flyway owns the schema — Hibernate must only validate
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }
}
