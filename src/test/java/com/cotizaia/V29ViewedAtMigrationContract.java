package com.cotizaia;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Spec 15 migration contract, shared by the H2 and the real-PostgreSQL
 * (Testcontainers) contexts: V29 must apply on both vendors, add a nullable
 * viewed_at column, and keep it a plain timestamp (NOT an encrypted text/blob
 * like the content columns, which are VARCHAR/TEXT). Mirrors the vendor-folders
 * rule: V29 is portable so it lives in db/migration, not in either vendor dir.
 */
abstract class V29ViewedAtMigrationContract {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void v29MigrationIsApplied() {
        Integer applied = jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '29' AND success = TRUE",
                Integer.class);
        assertThat(applied).isEqualTo(1);
    }

    @Test
    void viewedAtIsANullablePlainTimestampColumn() {
        Integer nullableColumn = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE LOWER(table_schema) = 'public' AND LOWER(table_name) = 'proposals' "
                        + "AND LOWER(column_name) = 'viewed_at' AND UPPER(is_nullable) = 'YES'",
                Integer.class);
        assertThat(nullableColumn).as("one nullable proposals.viewed_at column").isEqualTo(1);

        // "Not encrypted (plain timestamp, like approved_at)": an encrypted field
        // is stored as VARCHAR/TEXT/bytea; a plain timestamp is a TIMESTAMP type.
        String dataType = jdbc.queryForObject(
                "SELECT data_type FROM information_schema.columns "
                        + "WHERE LOWER(table_schema) = 'public' AND LOWER(table_name) = 'proposals' "
                        + "AND LOWER(column_name) = 'viewed_at'",
                String.class);
        assertThat(dataType.toLowerCase(Locale.ROOT)).as("plain timestamp column").contains("timestamp");
    }
}
