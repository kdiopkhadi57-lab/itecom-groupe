package com.elearning.repository;

import com.elearning.entity.SchoolEnrollment;
import com.elearning.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SchoolEnrollmentRepository extends JpaRepository<SchoolEnrollment, Long> {
    List<SchoolEnrollment> findAllByOrderByCreatedAtDesc();
    List<SchoolEnrollment> findByStudentOrderByAcademicYearDesc(User student);
    Optional<SchoolEnrollment> findByStudentAndAcademicYear(User student, String academicYear);
    List<SchoolEnrollment> findByAcademicYearAndLevel(String academicYear, String level);
    boolean existsByMatricule(String matricule);
    long countByAcademicYear(String academicYear);
}
