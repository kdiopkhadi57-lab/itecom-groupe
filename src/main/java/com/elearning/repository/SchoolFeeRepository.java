package com.elearning.repository;

import com.elearning.entity.SchoolFee;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SchoolFeeRepository extends JpaRepository<SchoolFee, Long> {
    List<SchoolFee> findAllByOrderByAcademicYearDescLevelAsc();
    List<SchoolFee> findByAcademicYearAndLevel(String academicYear, String level);
}
