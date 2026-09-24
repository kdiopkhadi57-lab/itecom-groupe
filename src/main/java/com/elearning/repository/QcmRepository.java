package com.elearning.repository;

import com.elearning.entity.Qcm;
import com.elearning.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface QcmRepository extends JpaRepository<Qcm, Long> {
    List<Qcm> findByProfessorOrderByCreatedAtDesc(User professor);
    List<Qcm> findByStatusOrderByCreatedAtDesc(String status);
    Optional<Qcm> findByTitle(String title);
}
