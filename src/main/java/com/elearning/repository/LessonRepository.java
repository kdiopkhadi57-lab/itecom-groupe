package com.elearning.repository;

import com.elearning.entity.Lesson;
import com.elearning.entity.Course;
import com.elearning.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface LessonRepository extends JpaRepository<Lesson, Long> {
    List<Lesson> findByCourseOrderByOrderIndexAsc(Course course);

    List<Lesson> findByCourseIsNullOrderByIdDesc();
    List<Lesson> findByTeacherAndCourseIsNullOrderByIdDesc(User teacher);
}
