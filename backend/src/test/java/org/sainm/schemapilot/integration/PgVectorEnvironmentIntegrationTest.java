package org.sainm.schemapilot.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "SCHEMAPILOT_IT_PGVECTOR", matches = "true")
class PgVectorEnvironmentIntegrationTest {
    @Test
    void pgvectorExtensionAndCosineSearchWorkAgainstRealPostgres() throws Exception {
        try (var connection = DriverManager.getConnection(requiredEnv("SCHEMAPILOT_IT_PG_URL"), requiredEnv("SCHEMAPILOT_IT_PG_USERNAME"), requiredEnv("SCHEMAPILOT_IT_PG_PASSWORD"));
             var statement = connection.createStatement()) {
            statement.execute("create extension if not exists vector");
            statement.execute("create temp table schemapilot_pgvector_it (id text primary key, embedding vector(3))");
            statement.execute("insert into schemapilot_pgvector_it (id, embedding) values ('near', '[1,0,0]'), ('far', '[0,1,0]')");
            try (var resultSet = statement.executeQuery("select id from schemapilot_pgvector_it order by embedding <=> '[0.9,0.1,0]'::vector limit 1")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString(1)).isEqualTo("near");
            }
        }
    }

    private String requiredEnv(String name) {
        var value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set when SCHEMAPILOT_IT_PGVECTOR=true.");
        }
        return value;
    }
}
