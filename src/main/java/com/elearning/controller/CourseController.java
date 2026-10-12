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

    private final com.elearning.service.VideoOptimizationService videoOptimizer;

    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final ProgressRepository progressRepository;
    private final VirtualClassRepository virtualClassRepository;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    // Public endpoints
    @GetMapping("/api/courses/public")
    public ResponseEntity<List<CourseResponse>> getPublicCourses(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userDetails != null ? userRepository.findByEmail(userDetails.getUsername()).orElse(null) : null;
        List<Course> courses;
        if (search != null && !search.isEmpty()) {
            courses = courseRepository.findByTitleContainingIgnoreCaseAndPublishedTrue(search);
        } else if (category != null && !category.isEmpty()) {
            courses = courseRepository.findByCategory(category).stream()
                .filter(Course::isPublished).collect(Collectors.toList());
        } else {
            courses = courseRepository.findByPublishedTrue();
        }
        return ResponseEntity.ok(courses.stream().filter(c -> forStudentLevel(c, user))
            .map(this::toCourseResponse).collect(Collectors.toList()));
    }

    @GetMapping("/api/courses/public/{id}")
    public ResponseEntity<CourseResponse> getPublicCourse(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        Course course = courseRepository.findById(id).orElseThrow();
        User user = userDetails != null ? userRepository.findByEmail(userDetails.getUsername()).orElse(null) : null;
        if (!forStudentLevel(course, user) && !user.getEnrolledCourses().contains(course)) {
            return ResponseEntity.status(403).build();
        }
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
        if (!forStudentLevel(course, user) && !user.getEnrolledCourses().contains(course)) {
            return ResponseEntity.status(403).body(ApiResponse.error("Ce cours n'est pas programmé pour votre niveau"));
        }
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
    public ResponseEntity<?> createCourse(
            @RequestBody Course courseData,
            @AuthenticationPrincipal UserDetails userDetails) {
        User teacher = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
        String level = Course.normalizeLevel(courseData.getLevel());
        if (level == null) return ResponseEntity.badRequest().body(java.util.Map.of("message", "Niveau invalide : choisissez L1, L2, L3, M1 ou M2."));
        courseData.setLevel(level);
        courseData.setTeacher(teacher);
        if (courseData.getLessons() != null) {
            courseData.getLessons().forEach(lesson -> lesson.setCourse(courseData));
        }
        Course saved = courseRepository.save(courseData);
        return ResponseEntity.ok(toCourseResponse(saved));
    }

    @PutMapping("/api/teacher/courses/{id}")
    public ResponseEntity<?> updateCourse(
            @PathVariable Long id,
            @RequestBody Course courseData,
            @AuthenticationPrincipal UserDetails userDetails) {
        Course course = courseRepository.findById(id).orElseThrow();
        if (!canManage(course, userDetails)) {
            return ResponseEntity.status(403).body(java.util.Map.of("message", "Vous ne pouvez modifier que vos propres cours"));
        }
        String level = Course.normalizeLevel(courseData.getLevel());
        if (level == null) return ResponseEntity.badRequest().body(java.util.Map.of("message", "Niveau invalide : choisissez L1, L2, L3, M1 ou M2."));
        course.setTitle(courseData.getTitle());
        course.setDescription(courseData.getDescription());
        course.setCategory(courseData.getCategory());
        course.setLevel(level);
        course.setPublished(courseData.isPublished());
        return ResponseEntity.ok(toCourseResponse(courseRepository.save(course)));
    }

    /** Suppression d'un cours par son professeur (ou un administrateur), avec ses leçons et la progression des étudiants. */
    @DeleteMapping("/api/teacher/courses/{id}")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<ApiResponse<String>> deleteCourse(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        Course course = courseRepository.findById(id).orElse(null);
        if (course == null) return ResponseEntity.status(404).body(ApiResponse.error("Cours introuvable"));
        if (!canManage(course, userDetails)) {
            return ResponseEntity.status(403).body(ApiResponse.error("Vous ne pouvez supprimer que vos propres cours"));
        }
        // Références vers le cours et ses leçons (clés étrangères) : retirées avant le cours lui-même
        progressRepository.deleteByCourse(course);
        course.getLessons().forEach(l -> progressRepository.deleteByLessonId(l.getId()));
        jdbcTemplate.update("DELETE FROM user_enrollments WHERE course_id = ?", id);
        virtualClassRepository.findByCourseId(id).forEach(vc -> vc.setCourse(null));
        courseRepository.delete(course);
        return ResponseEntity.ok(ApiResponse.success("Cours supprimé", null));
    }

    /** Un étudiant ne voit que les cours de son niveau (tous si son niveau n'est pas renseigné). */
    private static boolean forStudentLevel(Course course, User user) {
        if (user == null || user.getRole() != Role.ROLE_STUDENT || user.getLevel() == null || user.getLevel().isBlank()) return true;
        return user.getLevel().trim().equalsIgnoreCase(course.getLevel());
    }

    /** Un professeur ne gère que ses propres cours ; l'administrateur les gère tous. */
    private boolean canManage(Course course, UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
        boolean owner = course.getTeacher() != null && course.getTeacher().getId().equals(user.getId());
        return owner || user.getRole() == Role.ROLE_ADMIN;
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
        Progress progress = user == null ? null : progressRepository.findByUserAndLesson(user, lesson).orElse(null);
        boolean completed = progress != null && progress.isCompleted();
        return LessonResponse.builder()
            .id(lesson.getId()).courseId(lesson.getCourse() != null ? lesson.getCourse().getId() : null)
            .title(lesson.getTitle()).description(lesson.getDescription()).content(lesson.getContent())
            .videoUrl(videoOptimizer.bestUrl(lesson.getVideoUrl()))
            .videoLightUrl(videoOptimizer.lightUrl(lesson.getVideoUrl())).pdfUrl(lesson.getPdfUrl())
            .duration(lesson.getDuration()).orderIndex(lesson.getOrderIndex())
            .type(lesson.getType()).completed(completed)
            .progressPercentage(com.elearning.service.LessonProgressService.lessonPercent(progress))
            .videoPosition(progress != null ? progress.getVideoPosition() : null)
            .starterCode(lesson.getStarterCode()).language(lesson.getLanguage())
            .exercise(lesson.isExercise())
            .build();
    }
}
