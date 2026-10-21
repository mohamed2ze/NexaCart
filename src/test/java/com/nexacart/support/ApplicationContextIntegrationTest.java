package com.nexacart.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationContextIntegrationTest extends IntegrationTestBase {

    @Test
    void contextStartsAndFlywaySchemaIsValidated() {
        Integer appliedMigrations = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where success = true",
                Integer.class
        );
        assertThat(appliedMigrations).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'public'",
                Integer.class
        )).isGreaterThanOrEqualTo(13);
    }
}
