package com.elearning.controller;

import com.elearning.dto.request.LessonRequest;
import com.elearning.dto.response.ApiResponse;
import com.elearning.dto.response.LessonResponse;
import com.elearning.entity.*;
import com.elearning.repository.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Exercices autonomes (cas pratiques) : non rattachés à un cours, créés librement par
 * un professeur avec juste un titre + un type, puis ouverts en mode IDE/tableur.
 */
@RestController
@RequiredArgsConstructor
public class ExerciseController {

    private final LessonRepository lessonRepository;
    private final UserRepository userRepository;
    private final ProgressRepository progressRepository;

    /** Tous les exercices autonomes, visibles par tout utilisateur connecté. */
    @GetMapping("/api/exercises")
    public ResponseEntity<List<LessonResponse>> listAll(@AuthenticationPrincipal UserDetails ud) {
        User user = userRepository.findByEmail(ud.getUsername()).orElseThrow();
        return ResponseEntity.ok(lessonRepository.findByCourseIsNullOrderByIdDesc().stream()
            .map(l -> toResponse(l, user)).collect(Collectors.toList()));
    }

    /** Les exercices autonomes créés par le professeur connecté. */
    @GetMapping("/api/teacher/exercises")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ResponseEntity<List<LessonResponse>> listMine(@AuthenticationPrincipal UserDetails ud) {
        User teacher = userRepository.findByEmail(ud.getUsername()).orElseThrow();
        return ResponseEntity.ok(lessonRepository.findByTeacherAndCourseIsNullOrderByIdDesc(teacher).stream()
            .map(l -> toResponse(l, teacher)).collect(Collectors.toList()));
    }

    @PostMapping("/api/teacher/exercises")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ResponseEntity<LessonResponse> create(
            @Valid @RequestBody LessonRequest request,
            @AuthenticationPrincipal UserDetails ud) {
        User teacher = userRepository.findByEmail(ud.getUsername()).orElseThrow();
        Lesson lesson = Lesson.builder()
            .title(request.getTitle())
            .description(request.getDescription())
            .content(request.getContent())
            .videoUrl(request.getVideoUrl())
            .pdfUrl(request.getPdfUrl())
            .duration(request.getDuration())
            .orderIndex(0)
            .type(request.getType())
            .starterCode(request.getStarterCode())
            .language(request.getLanguage())
            .exercise(true)
            .teacher(teacher)
            .build();
        return ResponseEntity.ok(toResponse(lessonRepository.save(lesson), teacher));
    }

    @PutMapping("/api/teacher/exercises/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ResponseEntity<LessonResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody LessonRequest request,
            @AuthenticationPrincipal UserDetails ud) {
        User teacher = userRepository.findByEmail(ud.getUsername()).orElseThrow();
        Lesson lesson = lessonRepository.findById(id).orElseThrow();
        checkOwnership(lesson, teacher);

        lesson.setTitle(request.getTitle());
        lesson.setDescription(request.getDescription());
        lesson.setContent(request.getContent());
        lesson.setVideoUrl(request.getVideoUrl());
        lesson.setPdfUrl(request.getPdfUrl());
        lesson.setDuration(request.getDuration());
        lesson.setType(request.getType());
        lesson.setStarterCode(request.getStarterCode());
        lesson.setLanguage(request.getLanguage());
        return ResponseEntity.ok(toResponse(lessonRepository.save(lesson), teacher));
    }

    @DeleteMapping("/api/teacher/exercises/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Transactional
    public ResponseEntity<ApiResponse<String>> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails ud) {
        User teacher = userRepository.findByEmail(ud.getUsername()).orElseThrow();
        Lesson lesson = lessonRepository.findById(id).orElseThrow();
        checkOwnership(lesson, teacher);
        progressRepository.deleteByLessonId(id);
        lessonRepository.delete(lesson);
        return ResponseEntity.ok(ApiResponse.success("Exercice supprimé", null));
    }

    private void checkOwnership(Lesson lesson, User teacher) {
        boolean isOwner = lesson.getTeacher() != null && lesson.getTeacher().getId().equals(teacher.getId());
        if (teacher.getRole() != Role.ROLE_ADMIN && !isOwner) {
            throw new RuntimeException("Accès refusé : vous n'êtes pas l'auteur de cet exercice");
        }
    }

    private LessonResponse toResponse(Lesson lesson, User user) {
        boolean completed = progressRepository.findByUserAndLesson(user, lesson)
            .map(Progress::isCompleted).orElse(false);
        return LessonResponse.builder()
            .id(lesson.getId())
            .courseId(null)
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
            .exercise(true)
            .build();
    }
}
