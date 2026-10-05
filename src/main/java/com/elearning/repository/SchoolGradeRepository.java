package com.elearning.repository;

import com.elearning.entity.SchoolEnrollment;
import com.elearning.entity.SchoolGrade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SchoolGradeRepository extends JpaRepository<SchoolGrade, Long> {
    void deleteByEnrollment(SchoolEnrollment enrollment);
    List<SchoolGrade> findByEnrollmentOrderBySemesterAscSubjectAsc(SchoolEnrollment enrollment);
    List<SchoolGrade> findByEnrollmentAndPublishedTrueOrderBySemesterAscSubjectAsc(SchoolEnrollment enrollment);
    Optional<SchoolGrade> findByEnrollmentAndSemesterAndSubjectAndSession(SchoolEnrollment enrollment, String semester,
                                                                           String subject, String session);
    List<SchoolGrade> findByEnrollmentInAndSemesterAndSubjectAndSession(Collection<SchoolEnrollment> enrollments,
                                                                         String semester, String subject, String session);
    List<SchoolGrade> findByEnrollmentInAndSemesterAndPublishedFalse(Collection<SchoolEnrollment> enrollments, String semester);

    @Query("SELECT DISTINCT g.subject FROM SchoolGrade g WHERE g.enrollment.academicYear = ?1 AND g.enrollment.level = ?2 ORDER BY g.subject")
    List<String> findSubjects(String academicYear, String level);
}
