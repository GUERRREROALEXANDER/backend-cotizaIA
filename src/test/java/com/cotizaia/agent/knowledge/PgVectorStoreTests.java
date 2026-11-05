package com.cotizaia.agent.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Same contract on real PostgreSQL 16 + pgvector. Booting the context here also runs every Flyway migration
 * (V1 onwards, including the vendor-specific pgvector one) against PostgreSQL, not only H2.
 * Skipped, not failed, on machines without Docker.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {"llm.provider=stub", "knowledge.vector-store=pgvector"})
@ActiveProfiles("test")
class PgVectorStoreTests extends VectorStoreContract {

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

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void isThePgvectorImplementationOnARealVectorColumn() {
        assertThat(store).isInstanceOf(PgVectorStore.class);
        assertThat(store.name()).isEqualTo("pgvector");
        assertThat(jdbc.queryForObject("select count(*) from pg_extension where extname = 'vector'", Integer.class))
                .isEqualTo(1);
    }
}
