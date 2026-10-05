package com.elearning.controller;

import com.elearning.entity.*;
import com.elearning.repository.*;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Suivi d'un cours par son professeur : progression et temps passé de chaque étudiant.
 */
@RestController
@RequestMapping("/api/teacher/courses")
@RequiredArgsConstructor
public class TeacherCourseProgressController {

    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final ProgressRepository progressRepository;

    @Data static class LessonDto { Long id; String title; Integer orderIndex; }
    @Data static class LessonProgressDto { Long lessonId; boolean completed; double percentage; int timeSpentSeconds; }
    @Data static class StudentProgressDto {
        Long id; String firstName; String lastName; String email; String level;
        int completedLessons; double percentage; int timeSpentSeconds; String lastActivity;
        List<LessonProgressDto> lessons;
    }
    @Data static class CourseProgressDto {
        Long courseId; String courseTitle; int totalLessons;
        List<LessonDto> lessons; List<StudentProgressDto> students;
    }

    @GetMapping("/{id}/students-progress")
    @Transactional(readOnly = true)
    public ResponseEntity<?> studentsProgress(@PathVariable Long id, Authentication auth) {
        Course course = courseRepository.findById(id).orElse(null);
        if (course == null) return ResponseEntity.notFound().build();
        boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        boolean isOwner = course.getTeacher() != null && course.getTeacher().getEmail().equalsIgnoreCase(auth.getName());
        if (!isAdmin && !isOwner) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Ce cours ne vous appartient pas."));
        }

        List<Lesson> lessons = course.getLessons().stream()
            .sorted(Comparator.comparing(Lesson::getOrderIndex, Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
        int total = lessons.size();

        // Étudiants inscrits + ceux qui ont une progression sans inscription explicite
        Map<Long, List<Progress>> progressByUser = progressRepository.findByCourse(course).stream()
            .filter(p -> p.getUser() != null)
            .collect(Collectors.groupingBy(p -> p.getUser().getId()));
        Map<Long, User> students = new LinkedHashMap<>();
        userRepository.findByEnrolledCoursesContaining(course).stream()
            .filter(u -> u.getRole() == Role.ROLE_STUDENT)
            .forEach(u -> students.put(u.getId(), u));
        progressByUser.values().forEach(list -> {
            User u = list.get(0).getUser();
            if (u.getRole() == Role.ROLE_STUDENT) students.putIfAbsent(u.getId(), u);
        });

        CourseProgressDto dto = new CourseProgressDto();
        dto.courseId = course.getId();
        dto.courseTitle = course.getTitle();
        dto.totalLessons = total;
        dto.lessons = lessons.stream().map(l -> {
            LessonDto d = new LessonDto(); d.id = l.getId(); d.title = l.getTitle(); d.orderIndex = l.getOrderIndex(); return d;
        }).toList();

        dto.students = students.values().stream().map(u -> {
            List<Progress> list = progressByUser.getOrDefault(u.getId(), List.of());
            Map<Long, Progress> byLesson = list.stream().filter(p -> p.getLesson() != null)
                .collect(Collectors.toMap(p -> p.getLesson().getId(), p -> p, (a, b) -> a));

            StudentProgressDto s = new StudentProgressDto();
            s.id = u.getId(); s.firstName = u.getFirstName(); s.lastName = u.getLastName();
            s.email = u.getEmail(); s.level = u.getLevel();
            s.completedLessons = (int) lessons.stream().filter(l -> byLesson.containsKey(l.getId()) && byLesson.get(l.getId()).isCompleted()).count();
            // Même calcul que pour l'étudiant : les leçons commencées comptent pour leur part
            s.percentage = com.elearning.service.LessonProgressService.coursePercent(lessons, byLesson);
            s.timeSpentSeconds = list.stream().mapToInt(p -> p.getWatchedSeconds() == null ? 0 : p.getWatchedSeconds()).sum();
            s.lastActivity = list.stream().map(Progress::getLastUpdated).filter(Objects::nonNull)
                .max(LocalDateTime::compareTo).map(LocalDateTime::toString).orElse(null);
            s.lessons = lessons.stream().map(l -> {
                Progress p = byLesson.get(l.getId());
                LessonProgressDto lp = new LessonProgressDto();
                lp.lessonId = l.getId();
                lp.completed = p != null && p.isCompleted();
                lp.percentage = com.elearning.service.LessonProgressService.lessonPercent(p);
                lp.timeSpentSeconds = p != null && p.getWatchedSeconds() != null ? p.getWatchedSeconds() : 0;
                return lp;
            }).toList();
            return s;
        }).sorted(Comparator.comparing((StudentProgressDto s) -> s.lastName == null ? "" : s.lastName.toLowerCase())
            .thenComparing(s -> s.firstName == null ? "" : s.firstName.toLowerCase()))
            .toList();

        return ResponseEntity.ok(dto);
    }
}
