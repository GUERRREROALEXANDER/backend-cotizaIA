package com.cotizaia;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Same migration contract on real PostgreSQL 16 + pgvector. Booting the context
 * runs every Flyway migration (V1..V29, including the vendor-specific pgvector
 * one) against PostgreSQL, not only H2. Skipped, not failed, without Docker.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {"llm.provider=stub", "spec15.v29-postgres=true"})
@ActiveProfiles("test")
class V29ViewedAtMigrationPostgresTests extends V29ViewedAtMigrationContract {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }
}
