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
