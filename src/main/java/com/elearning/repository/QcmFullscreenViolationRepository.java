package com.elearning.repository;

import com.elearning.entity.QcmFullscreenViolation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QcmFullscreenViolationRepository extends JpaRepository<QcmFullscreenViolation, Long> {
    List<QcmFullscreenViolation> findByPassageIdOrderByViolationTimeAsc(Long passageId);
}
