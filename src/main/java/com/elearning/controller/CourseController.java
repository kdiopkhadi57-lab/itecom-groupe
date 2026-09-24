package com.elearning.controller;

import com.elearning.dto.response.*;
import com.elearning.entity.*;
import com.elearning.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class CourseController {

    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final ProgressRepository progressRepository;

    // Public endpoints
    @GetMapping("/api/courses/public")
    public ResponseEntity<List<CourseResponse>> getPublicCourses(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search) {
        List<Course> courses;
        if (search != null && !search.isEmpty()) {
            courses = courseRepository.findByTitleContainingIgnoreCaseAndPublishedTrue(search);
        } else if (category != null && !category.isEmpty()) {
            courses = courseRepository.findByCategory(category).stream()
                .filter(Course::isPublished).collect(Collectors.toList());
        } else {
            courses = courseRepository.findByPublishedTrue();
        }
        return ResponseEntity.ok(courses.stream().map(this::toCourseResponse).collect(Collectors.toList()));
    }

    @GetMapping("/api/courses/public/{id}")
    public ResponseEntity<CourseResponse> getPublicCourse(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        Course course = courseRepository.findById(id).orElseThrow();
        User user = userDetails != null ? userRepository.findByEmail(userDetails.getUsername()).orElse(null) : null;
        return ResponseEntity.ok(toCourseResponse(course, user));
    }

    // Protected endpoints
    @GetMapping("/api/courses/enrolled")
    public ResponseEntity<List<CourseResponse>> getEnrolledCourses(
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
        return ResponseEntity.ok(user.getEnrolledCourses().stream()
            .map(c -> toCourseResponse(c, user)).collect(Collectors.toList()));
    }

    @PostMapping("/api/courses/{id}/enroll")
    public ResponseEntity<ApiResponse<String>> enrollCourse(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
        Course course = courseRepository.findById(id).orElseThrow();
        if (!user.getEnrolledCourses().contains(course)) {
            user.getEnrolledCourses().add(course);
            userRepository.save(user);
        }
        return ResponseEntity.ok(ApiResponse.success("Inscription réussie", null));
    }

    // Teacher endpoints
    @GetMapping("/api/teacher/courses")
    public ResponseEntity<List<CourseResponse>> getTeacherCourses(
            @AuthenticationPrincipal UserDetails userDetails) {
        User teacher = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
        return ResponseEntity.ok(courseRepository.findByTeacher(teacher).stream()
            .map(this::toCourseResponse).collect(Collectors.toList()));
    }

    @PostMapping("/api/teacher/courses")
    public ResponseEntity<CourseResponse> createCourse(
            @RequestBody Course courseData,
            @AuthenticationPrincipal UserDetails userDetails) {
        User teacher = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
        courseData.setTeacher(teacher);
        if (courseData.getLessons() != null) {
            courseData.getLessons().forEach(lesson -> lesson.setCourse(courseData));
        }
        Course saved = courseRepository.save(courseData);
        return ResponseEntity.ok(toCourseResponse(saved));
    }

    @PutMapping("/api/teacher/courses/{id}")
    public ResponseEntity<CourseResponse> updateCourse(
            @PathVariable Long id,
            @RequestBody Course courseData) {
        Course course = courseRepository.findById(id).orElseThrow();
        course.setTitle(courseData.getTitle());
        course.setDescription(courseData.getDescription());
        course.setCategory(courseData.getCategory());
        course.setLevel(courseData.getLevel());
        course.setPublished(courseData.isPublished());
        return ResponseEntity.ok(toCourseResponse(courseRepository.save(course)));
    }

    private CourseResponse toCourseResponse(Course course) {
        return toCourseResponse(course, null);
    }

    private CourseResponse toCourseResponse(Course course, User user) {
        int totalDuration = course.getLessons().stream()
            .mapToInt(l -> l.getDuration() != null ? l.getDuration() : 0).sum();

        UserResponse teacher = course.getTeacher() != null ? UserResponse.builder()
            .id(course.getTeacher().getId())
            .firstName(course.getTeacher().getFirstName())
            .lastName(course.getTeacher().getLastName())
            .email(course.getTeacher().getEmail())
            .avatarUrl(course.getTeacher().getAvatarUrl())
            .build() : null;

        List<LessonResponse> lessons = course.getLessons().stream()
            .map(l -> toLessonResponse(l, user)).collect(Collectors.toList());

        return CourseResponse.builder()
            .id(course.getId()).title(course.getTitle()).description(course.getDescription())
            .thumbnailUrl(course.getThumbnailUrl()).category(course.getCategory())
            .level(course.getLevel()).published(course.isPublished())
            .teacher(teacher).lessons(lessons).totalLessons(course.getLessons().size())
            .totalDurationMinutes(totalDuration).createdAt(course.getCreatedAt()).build();
    }

    private LessonResponse toLessonResponse(Lesson lesson, User user) {
        boolean completed = user != null && progressRepository.findByUserAndLesson(user, lesson)
            .map(Progress::isCompleted).orElse(false);
        return LessonResponse.builder()
            .id(lesson.getId()).courseId(lesson.getCourse() != null ? lesson.getCourse().getId() : null)
            .title(lesson.getTitle()).description(lesson.getDescription()).content(lesson.getContent())
            .videoUrl(lesson.getVideoUrl()).pdfUrl(lesson.getPdfUrl())
            .duration(lesson.getDuration()).orderIndex(lesson.getOrderIndex())
            .type(lesson.getType()).completed(completed)
            .starterCode(lesson.getStarterCode()).language(lesson.getLanguage())
            .exercise(lesson.isExercise())
            .build();
    }
}
