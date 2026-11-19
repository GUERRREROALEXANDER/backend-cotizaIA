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
 * Same migration contract on real PostgreSQL 16 + pgvector, where V30's BOOLEAN default and
 * TIMESTAMP WITH TIME ZONE columns are checked by the real engine. Skipped, not failed, without Docker.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {"llm.provider=stub", "spec16.v30-postgres=true"})
@ActiveProfiles("test")
class V30EmailVerificationMigrationPostgresTests extends V30EmailVerificationMigrationContract {

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
