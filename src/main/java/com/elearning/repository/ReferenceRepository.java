package com.elearning.repository;

import com.elearning.entity.Reference;
import com.elearning.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ReferenceRepository extends JpaRepository<Reference, Long> {
    List<Reference> findByUserOrderByCreatedAtDesc(User user);
    List<Reference> findByUserAndCollectionOrderByCreatedAtDesc(User user, String collection);

    @Query("SELECT r FROM Reference r WHERE r.user = :user AND " +
           "(:q IS NULL OR LOWER(r.title) LIKE LOWER(CONCAT('%',:q,'%')) OR " +
           "LOWER(r.authors) LIKE LOWER(CONCAT('%',:q,'%')))")
    List<Reference> search(@Param("user") User user, @Param("q") String q);

    @Query("SELECT DISTINCT r.collection FROM Reference r WHERE r.user = :user AND r.collection IS NOT NULL")
    List<String> findCollectionsByUser(@Param("user") User user);
}
