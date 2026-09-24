package com.elearning.repository;

import com.elearning.entity.Course;
import com.elearning.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {
    List<Course> findByPublishedTrue();
    List<Course> findByTeacher(User teacher);
    List<Course> findByCategory(String category);
    List<Course> findByTitleContainingIgnoreCaseAndPublishedTrue(String keyword);
    Optional<Course> findByTitle(String title);
}
