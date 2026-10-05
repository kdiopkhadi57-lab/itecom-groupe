package com.elearning.repository;

import com.elearning.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:schoolrepo;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class SchoolRepositoryTest {

    @Autowired UserRepository users;
    @Autowired SchoolEnrollmentRepository enrollments;
    @Autowired SchoolPaymentRepository payments;
    @Autowired SchoolGradeRepository grades;
    @Autowired NotificationRepository notifications;

    @Test
    void queriesSumPaymentsListSubjectsAndNotifications() {
        User u = users.save(User.builder().firstName("Awa").lastName("Diop").email("awa@test.com").password("x")
            .role(Role.ROLE_STUDENT).build());
        SchoolEnrollment e = enrollments.save(SchoolEnrollment.builder().student(u).academicYear("2026-2027").level("L2")
            .matricule("ITC26-L2-0001").registrationFee(50_000).tuitionFee(450_000).build());

        payments.save(pay(e, 50_000, "VALIDATED", "A"));
        payments.save(pay(e, 30_000, "PENDING", "B"));
        payments.save(pay(e, 10_000, "REJECTED", "C"));
        assertEquals(50_000, payments.sumValidated(e));
        assertTrue(payments.existsByTransactionRefIgnoreCaseAndStatusNot("a", "REJECTED"));
        // Une référence refusée peut être déclarée à nouveau
        assertFalse(payments.existsByTransactionRefIgnoreCaseAndStatusNot("c", "REJECTED"));
        assertEquals(1, payments.countByStatus("PENDING"));

        grades.save(SchoolGrade.builder().enrollment(e).semester("S1").subject("Droit").grade(12).build());
        grades.save(SchoolGrade.builder().enrollment(e).semester("S2").subject("Comptabilité").grade(9).build());
        assertEquals(List.of("Comptabilité", "Droit"), grades.findSubjects("2026-2027", "L2"));
        assertEquals(1, grades.findByEnrollmentInAndSemesterAndPublishedFalse(List.of(e), "S1").size());

        notifications.save(Notification.builder().user(u).title("T").message("M").build());
        notifications.save(Notification.builder().user(u).title("T2").message("M2").build());
        assertEquals(2, notifications.countByUserAndReadFalse(u));
        assertEquals(2, notifications.markAllRead(u));
        assertEquals(0, notifications.countByUserAndReadFalse(u));
        assertEquals(2, notifications.findByUserOrderByCreatedAtDesc(u, PageRequest.of(0, 30)).size());
    }

    private static SchoolPayment pay(SchoolEnrollment e, long amount, String status, String ref) {
        return SchoolPayment.builder().enrollment(e).amount(amount).purpose("SCOLARITE").method("WAVE")
            .transactionRef(ref).status(status).submittedAt(LocalDateTime.now()).build();
    }
}
