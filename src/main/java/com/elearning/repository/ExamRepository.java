package com.elearning.repository;

import com.elearning.entity.Exam;
import com.elearning.entity.ExamStatus;
import com.elearning.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ExamRepository extends JpaRepository<Exam, Long> {
    List<Exam> findByProfessorOrderByCreatedAtDesc(User professor);
    List<Exam> findAllByOrderByCreatedAtDesc();
    List<Exam> findByStatus(ExamStatus status);
}
