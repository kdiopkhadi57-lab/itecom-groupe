package com.elearning.repository;

import com.elearning.entity.VirtualClassRollCall;
import com.elearning.entity.VirtualClassRollCallAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;

public interface VirtualClassRollCallAnswerRepository extends JpaRepository<VirtualClassRollCallAnswer, Long> {
    List<VirtualClassRollCallAnswer> findByRollCall(VirtualClassRollCall rollCall);
    List<VirtualClassRollCallAnswer> findByRollCallIn(Collection<VirtualClassRollCall> rollCalls);
    boolean existsByRollCallAndEmailIgnoreCase(VirtualClassRollCall rollCall, String email);
}
