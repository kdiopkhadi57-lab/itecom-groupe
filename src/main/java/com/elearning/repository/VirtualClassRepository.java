package com.elearning.repository;

import com.elearning.entity.VirtualClass;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface VirtualClassRepository extends JpaRepository<VirtualClass, Long> {
    List<VirtualClass> findByStatus(String status);
    List<VirtualClass> findByCourseId(Long courseId);
}
