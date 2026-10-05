package com.elearning.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Tables du module de scolarité (frais, inscriptions, paiements, notes, attestations, notifications).
 * En production Hibernate ne crée pas le schéma (ddl-auto=none) : on le crée ici s'il manque.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
@Slf4j
public class SchoolSchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        for (String sql : statements(isMysql())) {
            try {
                jdbcTemplate.execute(sql);
            } catch (Exception e) {
                log.warn("Scolarité : création de table impossible ({})", e.getMessage());
            }
        }
    }

    /** Ordres CREATE TABLE pour MySQL / MariaDB, ou PostgreSQL. */
    static List<String> statements(boolean mysql) {
        String id = mysql ? "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY" : "id BIGSERIAL PRIMARY KEY";
        String ts = mysql ? "DATETIME(6)" : "TIMESTAMP";
        String engine = mysql ? " ENGINE=InnoDB" : "";

        return List.of(
            "CREATE TABLE IF NOT EXISTS school_fees (" + id + ","
                + " academic_year VARCHAR(9) NOT NULL, level VARCHAR(5) NOT NULL, specialization VARCHAR(255),"
                + " registration_fee BIGINT NOT NULL, tuition_fee BIGINT NOT NULL, installments INT NOT NULL DEFAULT 1,"
                + " created_at " + ts + ")" + engine,
            "CREATE TABLE IF NOT EXISTS school_enrollments (" + id + ","
                + " student_id BIGINT NOT NULL, academic_year VARCHAR(9) NOT NULL, level VARCHAR(5) NOT NULL,"
                + " specialization VARCHAR(255), matricule VARCHAR(30) NOT NULL,"
                + " registration_fee BIGINT NOT NULL, tuition_fee BIGINT NOT NULL, discount BIGINT NOT NULL DEFAULT 0,"
                + " installments INT NOT NULL DEFAULT 1, status VARCHAR(20) NOT NULL DEFAULT 'PENDING',"
                + " created_at " + ts + ", updated_at " + ts + ","
                + " CONSTRAINT uk_school_enrollment_matricule UNIQUE (matricule),"
                + " CONSTRAINT uk_school_enrollment_year UNIQUE (student_id, academic_year),"
                + " CONSTRAINT fk_school_enrollment_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE)" + engine,
            "CREATE TABLE IF NOT EXISTS school_payments (" + id + ","
                + " enrollment_id BIGINT NOT NULL, amount BIGINT NOT NULL, purpose VARCHAR(20) NOT NULL,"
                + " method VARCHAR(20) NOT NULL, phone VARCHAR(255), transaction_ref VARCHAR(255),"
                + " status VARCHAR(20) NOT NULL DEFAULT 'PENDING', receipt_number VARCHAR(30), verification_code VARCHAR(20),"
                + " rejection_reason VARCHAR(500), processed_by VARCHAR(255),"
                + " submitted_at " + ts + " NOT NULL, processed_at " + ts + ","
                + " CONSTRAINT uk_school_payment_receipt UNIQUE (receipt_number),"
                + " CONSTRAINT uk_school_payment_code UNIQUE (verification_code),"
                + " CONSTRAINT fk_school_payment_enrollment FOREIGN KEY (enrollment_id) REFERENCES school_enrollments(id) ON DELETE CASCADE)" + engine,
            "CREATE TABLE IF NOT EXISTS school_grades (" + id + ","
                + " enrollment_id BIGINT NOT NULL, semester VARCHAR(5) NOT NULL, subject VARCHAR(255) NOT NULL,"
                + " coefficient DOUBLE PRECISION NOT NULL DEFAULT 1, grade DOUBLE PRECISION NOT NULL,"
                + " exam_session VARCHAR(12) NOT NULL DEFAULT 'NORMALE', comment VARCHAR(500),"
                + " published BOOLEAN NOT NULL DEFAULT FALSE, updated_at " + ts + ","
                + " CONSTRAINT uk_school_grade UNIQUE (enrollment_id, semester, subject, exam_session),"
                + " CONSTRAINT fk_school_grade_enrollment FOREIGN KEY (enrollment_id) REFERENCES school_enrollments(id) ON DELETE CASCADE)" + engine,
            "CREATE TABLE IF NOT EXISTS school_certificates (" + id + ","
                + " enrollment_id BIGINT NOT NULL, type VARCHAR(20) NOT NULL, verification_code VARCHAR(20) NOT NULL,"
                + " reference VARCHAR(30) NOT NULL, average DOUBLE PRECISION, mention VARCHAR(255),"
                + " issued_at " + ts + " NOT NULL, issued_by VARCHAR(255),"
                + " revoked BOOLEAN NOT NULL DEFAULT FALSE, revoked_at " + ts + ","
                + " CONSTRAINT uk_school_certificate_code UNIQUE (verification_code),"
                + " CONSTRAINT uk_school_certificate_ref UNIQUE (reference),"
                + " CONSTRAINT fk_school_certificate_enrollment FOREIGN KEY (enrollment_id) REFERENCES school_enrollments(id) ON DELETE CASCADE)" + engine,
            "CREATE TABLE IF NOT EXISTS school_documents (" + id + ","
                + " enrollment_id BIGINT NOT NULL, type VARCHAR(30) NOT NULL, original_name VARCHAR(255) NOT NULL,"
                + " stored_name VARCHAR(255) NOT NULL, size BIGINT NOT NULL, uploaded_at " + ts + " NOT NULL,"
                + " CONSTRAINT fk_school_document_enrollment FOREIGN KEY (enrollment_id) REFERENCES school_enrollments(id) ON DELETE CASCADE)" + engine,
            "CREATE TABLE IF NOT EXISTS notifications (" + id + ","
                + " user_id BIGINT NOT NULL, title VARCHAR(255) NOT NULL, message VARCHAR(1000) NOT NULL, link VARCHAR(255),"
                + " category VARCHAR(20) NOT NULL DEFAULT 'INFO', is_read BOOLEAN NOT NULL DEFAULT FALSE, created_at " + ts + ","
                + " CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE)" + engine
        );
    }

    private boolean isMysql() {
        try {
            return Boolean.TRUE.equals(jdbcTemplate.execute((ConnectionCallback<Boolean>) con -> {
                String product = con.getMetaData().getDatabaseProductName();
                String p = product == null ? "" : product.toLowerCase(Locale.ROOT);
                return p.contains("mysql") || p.contains("mariadb");
            }));
        } catch (Exception e) {
            return false;
        }
    }
}
