package com.elearning.repository;

import com.elearning.entity.SchoolCertificate;
import com.elearning.entity.SchoolEnrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SchoolCertificateRepository extends JpaRepository<SchoolCertificate, Long> {
    List<SchoolCertificate> findAllByOrderByIssuedAtDesc();
    List<SchoolCertificate> findByEnrollmentOrderByIssuedAtDesc(SchoolEnrollment enrollment);
    Optional<SchoolCertificate> findByVerificationCode(String verificationCode);
    boolean existsByReference(String reference);
    long countByRevokedFalse();
}
