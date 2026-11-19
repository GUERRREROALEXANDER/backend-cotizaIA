package com.cotizaia;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Spec 16 migration contract, shared by the H2 and the real-PostgreSQL (Testcontainers) contexts:
 * V30 must apply on both vendors, flags the real table ({@code users}, not {@code app_users}) with a
 * NOT NULL boolean that defaults to TRUE, and creates the token table with exactly the agreed
 * columns, a unique hash and a cascade to its user.
 */
abstract class V30EmailVerificationMigrationContract {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void v30MigrationIsApplied() {
        Integer applied = jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '30' AND success = TRUE",
                Integer.class);
        assertThat(applied).isEqualTo(1);
    }

    @Test
    void usersEmailVerifiedIsANonNullBooleanThatDefaultsToTrue() {
        Map<String, Object> column = jdbc.queryForMap(
                "SELECT is_nullable, data_type, column_default FROM information_schema.columns "
                        + "WHERE LOWER(table_schema) = 'public' AND LOWER(table_name) = 'users' "
                        + "AND LOWER(column_name) = 'email_verified'");

        assertThat(String.valueOf(column.get("is_nullable"))).isEqualToIgnoringCase("NO");
        assertThat(String.valueOf(column.get("data_type")).toLowerCase(Locale.ROOT)).contains("boolean");
        assertThat(String.valueOf(column.get("column_default")))
                .as("existing accounts stay verified")
                .isEqualToIgnoringCase("true");
    }

    @Test
    void tokenTableHasTheAgreedColumnsAndSingleUseEvidence() {
        assertThat(columnsOf("email_verification_tokens"))
                .containsExactlyInAnyOrder("id", "user_id", "token_hash", "expires_at", "used_at");

        Map<String, Object> hash = jdbc.queryForMap(
                "SELECT data_type, character_maximum_length, is_nullable FROM information_schema.columns "
                        + "WHERE LOWER(table_schema) = 'public' AND LOWER(table_name) = 'email_verification_tokens' "
                        + "AND LOWER(column_name) = 'token_hash'");
        assertThat(String.valueOf(hash.get("data_type")).toLowerCase(Locale.ROOT)).contains("char");
        assertThat(((Number) hash.get("character_maximum_length")).intValue()).isEqualTo(64);
        assertThat(String.valueOf(hash.get("is_nullable"))).isEqualToIgnoringCase("NO");

        Map<String, Object> usedAt = jdbc.queryForMap(
                "SELECT is_nullable FROM information_schema.columns "
                        + "WHERE LOWER(table_schema) = 'public' AND LOWER(table_name) = 'email_verification_tokens' "
                        + "AND LOWER(column_name) = 'used_at'");
        assertThat(String.valueOf(usedAt.get("is_nullable")))
                .as("used_at is only written when the link is redeemed")
                .isEqualToIgnoringCase("YES");

        Integer uniqueHash = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints constraints "
                        + "WHERE LOWER(constraints.table_name) = 'email_verification_tokens' "
                        + "AND constraints.constraint_type = 'UNIQUE'",
                Integer.class);
        assertThat(uniqueHash).as("one row per token hash").isEqualTo(1);
    }

    @Test
    void tokenRowRequiresARealUser() {
        Integer foreignKeys = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints constraints "
                        + "WHERE LOWER(constraints.table_name) = 'email_verification_tokens' "
                        + "AND constraints.constraint_type = 'FOREIGN KEY'",
                Integer.class);
        assertThat(foreignKeys).isEqualTo(1);
    }

    private java.util.List<String> columnsOf(String table) {
        return jdbc.queryForList(
                "SELECT LOWER(column_name) FROM information_schema.columns "
                        + "WHERE LOWER(table_schema) = 'public' AND LOWER(table_name) = ?",
                String.class, table);
    }
}
