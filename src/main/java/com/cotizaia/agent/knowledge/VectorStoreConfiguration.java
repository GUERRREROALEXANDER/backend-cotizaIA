package com.cotizaia.agent.knowledge;

import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Picks the Bridge implementor by configuration: the portable exact scan
 * everywhere, or pgvector where the extension exists (prod/Supabase, enabled
 * by vendor migration V24).
 */
@Configuration
public class VectorStoreConfiguration {

    @Bean
    public VectorStore vectorStore(JdbcTemplate jdbc,
            @Value("${knowledge.vector-store:jdbc}") String selected) {
        String value = selected == null ? "" : selected.toLowerCase(Locale.ROOT);
        return switch (value) {
            case "jdbc" -> new JdbcVectorStore(jdbc);
            case "pgvector" -> new PgVectorStore(jdbc);
            default -> throw new IllegalStateException(
                    "Unknown knowledge.vector-store: " + selected + "; allowed: jdbc, pgvector");
        };
    }
}
