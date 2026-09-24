package com.elearning.repository;

import com.elearning.entity.VirtualClassStudent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VirtualClassStudentRepository extends JpaRepository<VirtualClassStudent, Long> {
    List<VirtualClassStudent> findByVirtualClassId(Long virtualClassId);
}
