package com.elearning.config;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class SchoolSchemaInitializerTest {

    @Test
    void mysqlAndPostgresStatementsCreateTheSchoolTables() {
        runOn("jdbc:h2:mem:mysqlschema;MODE=MySQL;DATABASE_TO_LOWER=TRUE", true);
        runOn("jdbc:h2:mem:pgschema;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE", false);
    }

    @Test
    void addsVideoProgressColumnsToAnExistingProgressTable() throws Exception {
        var ds = new org.springframework.jdbc.datasource.DriverManagerDataSource("jdbc:h2:mem:progresscols;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE users (id BIGINT PRIMARY KEY)");
        jdbc.execute("CREATE TABLE progress (id BIGINT PRIMARY KEY, percentage DOUBLE PRECISION)");
        SchoolSchemaInitializer init = new SchoolSchemaInitializer(jdbc);
        init.run(null);
        init.run(null);   // second démarrage : rien à refaire, aucune erreur
        jdbc.execute("INSERT INTO progress (id, video_position, video_duration, watched_ranges) VALUES (1, 12.5, 300, '[[0,12.5]]')");
        org.junit.jupiter.api.Assertions.assertEquals("[[0,12.5]]",
            jdbc.queryForObject("SELECT watched_ranges FROM progress WHERE id = 1", String.class));
    }

    private void runOn(String url, boolean mysql) {
        assertDoesNotThrow(() -> {
            try (Connection c = DriverManager.getConnection(url, "sa", ""); Statement st = c.createStatement()) {
                st.execute("CREATE TABLE users (id BIGINT PRIMARY KEY)");
                for (String sql : SchoolSchemaInitializer.statements(mysql)) st.execute(sql);
                // Idempotent : relancer au démarrage suivant ne doit pas échouer
                for (String sql : SchoolSchemaInitializer.statements(mysql)) st.execute(sql);
                st.execute("INSERT INTO users (id) VALUES (1)");
                st.execute("INSERT INTO school_enrollments (student_id, academic_year, level, matricule, registration_fee, tuition_fee) "
                    + "VALUES (1, '2026-2027', 'L1', 'ITC26-L1-0001', 50000, 400000)");
            }
        });
    }
}
