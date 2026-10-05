package com.elearning.controller;

import com.elearning.dto.response.*;
import com.elearning.entity.*;
import com.elearning.repository.*;
import com.elearning.service.LessonProgressService;
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
            .orElse(Progress.builder().user(user).lesson(lesson).course(lesson.getCourse()).percentage(0.0).build());
        // Vidéo de la plateforme : terminée en la regardant, pas d'un simple clic
        if (LessonProgressService.isTrackedVideo(lesson) && !progress.isCompleted()) {
            double seen = progress.getPercentage() == null ? 0 : progress.getPercentage();
            throw new IllegalArgumentException(String.format(java.util.Locale.FRANCE,
                "Regardez la vidéo jusqu'au bout pour terminer la leçon (au moins %.0f %%) : vous en avez vu %.0f %%.",
                LessonProgressService.VIDEO_COMPLETION_PERCENT, seen));
        }
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

        // La progression d'une vidéo se mesure en la regardant, pas en faisant défiler la page
        if (LessonProgressService.isTrackedVideo(lesson)) {
            return ResponseEntity.ok(ApiResponse.success("Progression suivie par la lecture de la vidéo", null));
        }
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

    public record VideoWatch(Double duration, Double position, List<double[]> ranges) {}

    /**
     * Lecture d'une vidéo : plages réellement regardées, position de reprise et durée. Le serveur fusionne
     * les plages (envois répétés ou rejoués après une période hors connexion sans double compte).
     */
    @PostMapping("/lesson/{lessonId}/video")
    public ResponseEntity<ApiResponse<Map<String, Object>>> videoProgress(
            @PathVariable Long lessonId,
            @RequestBody VideoWatch body,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
        Lesson lesson = lessonRepository.findById(lessonId).orElseThrow();
        Progress progress = progressRepository.findByUserAndLesson(user, lesson)
            .orElse(Progress.builder().user(user).lesson(lesson).course(lesson.getCourse()).percentage(0.0).build());
        LessonProgressService.applyVideoWatch(progress,
            body.duration() == null ? 0 : body.duration(), body.position() == null ? 0 : body.position(), body.ranges());
        progressRepository.save(progress);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("percentage", progress.getPercentage());
        data.put("completed", progress.isCompleted());
        data.put("position", progress.getVideoPosition());
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    /** Temps maximal accepté par envoi : le lecteur envoie un battement toutes les 30 s. */
    private static final int MAX_SECONDS_PER_PING = 120;

    /**
     * Ajoute du temps passé sur une leçon (battements envoyés par le lecteur de cours
     * tant que la page est visible). Sert au suivi « temps passé » côté professeur.
     */
    @PostMapping("/lesson/{lessonId}/time")
    public ResponseEntity<ApiResponse<String>> addTimeSpent(
            @PathVariable Long lessonId,
            @RequestBody Map<String, Integer> body,
            @AuthenticationPrincipal UserDetails userDetails) {
        int seconds = Math.min(MAX_SECONDS_PER_PING, Math.max(0, body.getOrDefault("seconds", 0)));
        if (seconds == 0) return ResponseEntity.ok(ApiResponse.success("Aucun temps ajouté", null));
        User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
        Lesson lesson = lessonRepository.findById(lessonId).orElseThrow();

        Progress progress = progressRepository.findByUserAndLesson(user, lesson)
            .orElse(Progress.builder().user(user).lesson(lesson).course(lesson.getCourse()).percentage(0.0).build());
        int current = progress.getWatchedSeconds() == null ? 0 : progress.getWatchedSeconds();
        progress.setWatchedSeconds(current + seconds);
        progressRepository.save(progress);
        return ResponseEntity.ok(ApiResponse.success("Temps enregistré", null));
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
        return ResponseEntity.ok(courseProgress(user, course));
    }

    /** Leçons terminées et progression du cours (les leçons commencées comptent pour leur part). */
    private ProgressResponse courseProgress(User user, Course course) {
        List<Lesson> lessons = course.getLessons();
        Map<Long, Progress> byLesson = new HashMap<>();
        for (Progress p : progressRepository.findByUserAndCourse(user, course)) {
            if (p.getLesson() != null) byLesson.put(p.getLesson().getId(), p);
        }
        return ProgressResponse.builder()
            .courseId(course.getId()).courseTitle(course.getTitle())
            .totalLessons(lessons.size())
            .completedLessons((int) LessonProgressService.completedCount(lessons, byLesson))
            .overallPercentage(LessonProgressService.coursePercent(lessons, byLesson)).build();
    }

    @GetMapping("/my-progress")
    public ResponseEntity<List<ProgressResponse>> getMyProgress(
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
        List<ProgressResponse> result = user.getEnrolledCourses().stream()
            .map(course -> courseProgress(user, course)).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }
}
