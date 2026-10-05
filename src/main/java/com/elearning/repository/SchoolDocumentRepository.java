package com.elearning.repository;

import com.elearning.entity.SchoolDocument;
import com.elearning.entity.SchoolEnrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SchoolDocumentRepository extends JpaRepository<SchoolDocument, Long> {
    List<SchoolDocument> findByEnrollmentOrderByTypeAscUploadedAtAsc(SchoolEnrollment enrollment);
}
