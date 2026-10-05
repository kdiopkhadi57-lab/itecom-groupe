package com.elearning.repository;

import com.elearning.entity.SchoolEnrollment;
import com.elearning.entity.SchoolPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface SchoolPaymentRepository extends JpaRepository<SchoolPayment, Long> {
    List<SchoolPayment> findAllByOrderBySubmittedAtDesc();
    List<SchoolPayment> findByStatusOrderBySubmittedAtDesc(String status);
    List<SchoolPayment> findByEnrollmentOrderBySubmittedAtDesc(SchoolEnrollment enrollment);
    long countByStatus(String status);
    boolean existsByTransactionRefIgnoreCaseAndStatusNot(String transactionRef, String status);
    boolean existsByReceiptNumber(String receiptNumber);
    Optional<SchoolPayment> findByVerificationCode(String verificationCode);
    boolean existsByEnrollment(SchoolEnrollment enrollment);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM SchoolPayment p WHERE p.enrollment = ?1 AND p.status = 'VALIDATED'")
    long sumValidated(SchoolEnrollment enrollment);
}
