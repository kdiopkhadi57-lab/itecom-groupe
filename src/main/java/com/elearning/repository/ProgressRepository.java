package com.elearning.repository;

import com.elearning.entity.Progress;
import com.elearning.entity.User;
import com.elearning.entity.Course;
import com.elearning.entity.Lesson;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProgressRepository extends JpaRepository<Progress, Long> {
    List<Progress> findByUserAndCourse(User user, Course course);
    Optional<Progress> findByUserAndLesson(User user, Lesson lesson);

    @Query("SELECT COUNT(p) FROM Progress p WHERE p.user = :user AND p.course = :course AND p.completed = true")
    long countCompletedLessonsByUserAndCourse(User user, Course course);

    void deleteByLessonId(Long lessonId);
}
