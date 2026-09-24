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
@RequestMapping("/api/progress")
@RequiredArgsConstructor
public class ProgressController {

    private final ProgressRepository progressRepository;
    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;

    @PostMapping("/lesson/{lessonId}/complete")
    public ResponseEntity<ApiResponse<String>> completeLesson(
            @PathVariable Long lessonId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
        Lesson lesson = lessonRepository.findById(lessonId).orElseThrow();

        Progress progress = progressRepository.findByUserAndLesson(user, lesson)
            .orElse(Progress.builder().user(user).lesson(lesson).course(lesson.getCourse()).build());
        progress.setCompleted(true);
        progress.setPercentage(100.0);
        progressRepository.save(progress);
        return ResponseEntity.ok(ApiResponse.success("Leçon marquée comme complète", null));
    }

    /**
     * Mise à jour incrémentale envoyée pendant la lecture (défilement de la page).
     * Marque automatiquement la leçon comme complétée une fois la fin de page atteinte.
     */
    @PostMapping("/lesson/{lessonId}/scroll")
    public ResponseEntity<ApiResponse<String>> updateScrollProgress(
            @PathVariable Long lessonId,
            @RequestBody Map<String, Double> body,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
        Lesson lesson = lessonRepository.findById(lessonId).orElseThrow();
        double percentage = Math.min(100.0, Math.max(0.0, body.getOrDefault("percentage", 0.0)));

        Progress progress = progressRepository.findByUserAndLesson(user, lesson)
            .orElse(Progress.builder().user(user).lesson(lesson).course(lesson.getCourse()).percentage(0.0).build());

        if (!progress.isCompleted() && percentage > (progress.getPercentage() == null ? 0.0 : progress.getPercentage())) {
            progress.setPercentage(percentage);
            if (percentage >= 95.0) {
                progress.setCompleted(true);
                progress.setPercentage(100.0);
            }
            progressRepository.save(progress);
        }
        return ResponseEntity.ok(ApiResponse.success("Progression mise à jour", null));
    }

    /** Sauvegarde le travail en cours (code / projet de packages-classes) sur une leçon ou un exercice. */
    @PostMapping("/lesson/{lessonId}/save-code")
    public ResponseEntity<ApiResponse<String>> saveCode(
            @PathVariable Long lessonId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
        Lesson lesson = lessonRepository.findById(lessonId).orElseThrow();

        Progress progress = progressRepository.findByUserAndLesson(user, lesson)
            .orElse(Progress.builder().user(user).lesson(lesson).course(lesson.getCourse()).percentage(0.0).build());
        progress.setSavedCode(body.get("code"));
        progressRepository.save(progress);
        return ResponseEntity.ok(ApiResponse.success("Travail sauvegardé", null));
    }

    @GetMapping("/lesson/{lessonId}/saved-code")
    public ResponseEntity<ApiResponse<Map<String, String>>> getSavedCode(
            @PathVariable Long lessonId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
        Lesson lesson = lessonRepository.findById(lessonId).orElseThrow();

        String saved = progressRepository.findByUserAndLesson(user, lesson)
            .map(Progress::getSavedCode).orElse(null);
        Map<String, String> data = new HashMap<>();
        data.put("code", saved);
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @GetMapping("/course/{courseId}")
    public ResponseEntity<ProgressResponse> getCourseProgress(
            @PathVariable Long courseId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
        Course course = courseRepository.findById(courseId).orElseThrow();

        int total = course.getLessons().size();
        long completed = progressRepository.countCompletedLessonsByUserAndCourse(user, course);
        double percentage = total > 0 ? (completed * 100.0 / total) : 0;

        return ResponseEntity.ok(ProgressResponse.builder()
            .courseId(courseId).courseTitle(course.getTitle())
            .totalLessons(total).completedLessons((int)completed)
            .overallPercentage(percentage).build());
    }

    @GetMapping("/my-progress")
    public ResponseEntity<List<ProgressResponse>> getMyProgress(
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
        List<ProgressResponse> result = user.getEnrolledCourses().stream().map(course -> {
            int total = course.getLessons().size();
            long completed = progressRepository.countCompletedLessonsByUserAndCourse(user, course);
            double pct = total > 0 ? (completed * 100.0 / total) : 0;
            return ProgressResponse.builder()
                .courseId(course.getId()).courseTitle(course.getTitle())
                .totalLessons(total).completedLessons((int)completed)
                .overallPercentage(pct).build();
        }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }
}
