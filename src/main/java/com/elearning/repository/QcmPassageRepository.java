package com.elearning.repository;

import com.elearning.entity.Qcm;
import com.elearning.entity.QcmPassage;
import com.elearning.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface QcmPassageRepository extends JpaRepository<QcmPassage, Long> {
    Optional<QcmPassage> findByQcmAndStudent(Qcm qcm, User student);
    List<QcmPassage> findByQcmAndIsSubmittedTrue(Qcm qcm);
    List<QcmPassage> findByStudentOrderByStartedAtDesc(User student);
}
