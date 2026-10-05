package com.elearning.repository;

import com.elearning.entity.VirtualClass;
import com.elearning.entity.VirtualClassRollCall;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface VirtualClassRollCallRepository extends JpaRepository<VirtualClassRollCall, Long> {
    List<VirtualClassRollCall> findByVirtualClassOrderByStartedAtAsc(VirtualClass virtualClass);
}
