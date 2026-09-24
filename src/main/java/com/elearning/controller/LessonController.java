package com.elearning.controller;

import com.elearning.dto.request.LessonRequest;
import com.elearning.dto.response.ApiResponse;
import com.elearning.dto.response.LessonResponse;
import com.elearning.entity.*;
import com.elearning.repository.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class LessonController {

    private final LessonRepository lessonRepository;
    private final CourseRepository courseRepository;
    private final ProgressRepository progressRepository;
    private final UserRepository userRepository;

    @GetMapping("/api/lessons/{id}")
    public ResponseEntity<LessonResponse> getLesson(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        Lesson lesson = lessonRepository.findById(id).orElseThrow();
        boolean completed = false;
        if (userDetails != null) {
            User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
            completed = progressRepository.findByUserAndLesson(user, lesson)
                .map(Progress::isCompleted).orElse(false);
        }
        return ResponseEntity.ok(toLessonResponse(lesson, completed));
    }

    @PostMapping("/api/teacher/courses/{courseId}/lessons")
    public ResponseEntity<LessonResponse> createLesson(
            @PathVariable Long courseId,
            @Valid @RequestBody LessonRequest request) {
        Course course = courseRepository.findById(courseId).orElseThrow();
        Lesson lesson = Lesson.builder()
            .title(request.getTitle())
            .description(request.getDescription())
            .content(request.getContent())
            .videoUrl(request.getVideoUrl())
            .pdfUrl(request.getPdfUrl())
            .duration(request.getDuration())
            .orderIndex(request.getOrderIndex() != null ? request.getOrderIndex() : course.getLessons().size())
            .type(request.getType())
            .starterCode(request.getStarterCode())
            .language(request.getLanguage())
            .exercise(Boolean.TRUE.equals(request.getExercise()))
            .course(course)
            .build();
        return ResponseEntity.ok(toLessonResponse(lessonRepository.save(lesson), false));
    }

    @PutMapping("/api/teacher/lessons/{id}")
    public ResponseEntity<LessonResponse> updateLesson(
            @PathVariable Long id,
            @Valid @RequestBody LessonRequest request) {
        Lesson lesson = lessonRepository.findById(id).orElseThrow();
        lesson.setTitle(request.getTitle());
        lesson.setDescription(request.getDescription());
        lesson.setContent(request.getContent());
        lesson.setVideoUrl(request.getVideoUrl());
        lesson.setPdfUrl(request.getPdfUrl());
        lesson.setDuration(request.getDuration());
        if (request.getOrderIndex() != null) lesson.setOrderIndex(request.getOrderIndex());
        lesson.setType(request.getType());
        lesson.setStarterCode(request.getStarterCode());
        lesson.setLanguage(request.getLanguage());
        lesson.setExercise(Boolean.TRUE.equals(request.getExercise()));
        return ResponseEntity.ok(toLessonResponse(lessonRepository.save(lesson), false));
    }

    @DeleteMapping("/api/teacher/lessons/{id}")
    @Transactional
    public ResponseEntity<ApiResponse<String>> deleteLesson(@PathVariable Long id) {
        // Les progressions des étudiants référencent la leçon (clé étrangère) : il faut les
        // supprimer avant la leçon elle-même, sinon la base refuse la suppression.
        progressRepository.deleteByLessonId(id);
        lessonRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success("Leçon supprimée", null));
    }

    private LessonResponse toLessonResponse(Lesson lesson, boolean completed) {
        return LessonResponse.builder()
            .id(lesson.getId())
            .courseId(lesson.getCourse() != null ? lesson.getCourse().getId() : null)
            .title(lesson.getTitle())
            .description(lesson.getDescription())
            .content(lesson.getContent())
            .videoUrl(lesson.getVideoUrl())
            .pdfUrl(lesson.getPdfUrl())
            .duration(lesson.getDuration())
            .orderIndex(lesson.getOrderIndex())
            .type(lesson.getType())
            .completed(completed)
            .starterCode(lesson.getStarterCode())
            .language(lesson.getLanguage())
            .exercise(lesson.isExercise())
            .build();
    }
}
