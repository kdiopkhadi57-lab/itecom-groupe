package com.elearning.repository;

import com.elearning.entity.Exam;
import com.elearning.entity.ExamStudent;
import com.elearning.entity.StudentExamStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExamStudentRepository extends JpaRepository<ExamStudent, Long> {
    Optional<ExamStudent> findByAccessToken(String accessToken);
    Optional<ExamStudent> findByExamAndStudentEmail(Exam exam, String studentEmail);
    List<ExamStudent> findByExamId(Long examId);
    List<ExamStudent> findByExamIdAndStatus(Long examId, StudentExamStatus status);
    List<ExamStudent> findByStudentEmailOrderByExam_CreatedAtDesc(String studentEmail);
}
