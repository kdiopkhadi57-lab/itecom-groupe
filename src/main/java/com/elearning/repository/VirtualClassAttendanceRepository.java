package com.elearning.repository;

import com.elearning.entity.VirtualClass;
import com.elearning.entity.VirtualClassAttendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface VirtualClassAttendanceRepository extends JpaRepository<VirtualClassAttendance, Long> {
    List<VirtualClassAttendance> findByVirtualClassOrderByJoinedAtAsc(VirtualClass virtualClass);
}
