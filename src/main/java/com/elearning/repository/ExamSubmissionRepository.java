package com.elearning.repository;

import com.elearning.entity.ExamSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ExamSubmissionRepository extends JpaRepository<ExamSubmission, Long> {

    @Query("SELECT s FROM ExamSubmission s WHERE s.isGraded = false AND s.submittedAt <= :deadline")
    List<ExamSubmission> findUngraded(LocalDateTime deadline);

    List<ExamSubmission> findByExamStudentExamId(Long examId);
}
