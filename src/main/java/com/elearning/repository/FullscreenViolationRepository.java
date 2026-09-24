package com.elearning.repository;

import com.elearning.entity.FullscreenViolation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FullscreenViolationRepository extends JpaRepository<FullscreenViolation, Long> {
    List<FullscreenViolation> findBySubmissionIdOrderByViolationTimeAsc(Long submissionId);
}
