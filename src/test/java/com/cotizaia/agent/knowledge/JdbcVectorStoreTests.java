package com.cotizaia.agent.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** The portable store (exact cosine in Java over a BYTEA/VARBINARY column) on the H2 test database. */
@SpringBootTest(properties = {"llm.provider=stub", "knowledge.vector-store=jdbc"})
@ActiveProfiles("test")
class JdbcVectorStoreTests extends VectorStoreContract {

    @Test
    void isTheJdbcImplementation() {
        assertThat(store).isInstanceOf(JdbcVectorStore.class);
        assertThat(store.name()).isEqualTo("jdbc");
    }
}
