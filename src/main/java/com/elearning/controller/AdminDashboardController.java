package com.elearning.controller;

import com.elearning.entity.*;
import com.elearning.repository.*;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Tableau de bord de l'administrateur : statistiques des cours en ligne, étudiants suivis,
 * rapport de chaque cours, devoirs / examens et rapport des évaluations par niveau.
 */
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private static final String NO_LEVEL = "Non renseigné";

    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final ProgressRepository progressRepository;
    private final QcmRepository qcmRepository;
    private final QcmPassageRepository passageRepository;
    private final ExamRepository examRepository;
    private final com.elearning.service.QcmAudienceService audienceService;

    @Data static class CourseStats {
        int total; int published; int lessons; int enrolledStudents; int activeStudents;
        double averageProgress; long timeSpentSeconds;
    }
    @Data static class CourseReport {
        Long id; String title; String teacher; String level; String category; boolean published;
        int lessons; int enrolled; int active; int completed; double averageProgress; long timeSpentSeconds;
    }
    @Data static class FollowedStudent {
        Long id; String firstName; String lastName; String email; String level;
        int courses; int completedLessons; double averageProgress; long timeSpentSeconds; String lastActivity;
    }
    @Data static class ExamStats {
        int devoirs; int devoirsPublished; int examens; int examensPublished;
        int expectedCopies; int submittedCopies; Double averageOn20; Double passRate;
    }
    @Data static class ExamReport {
        Long id; String type; String title; String teacher; String status; String createdAt;
        int assigned; int submitted; Double averageOn20; Double passRate;
    }
    @Data static class LevelReport {
        String level; int evaluations; int assigned; int submitted; int excluded;
        Double averageOn20; Double passRate; Double best; Double worst;
    }
    @Data static class DashboardDto {
        CourseStats courses; List<CourseReport> courseReports; List<FollowedStudent> followedStudents;
        ExamStats exams; List<ExamReport> examReports; List<LevelReport> levelReports;
    }

    /** Note sur 20 d'une copie remise, avec l'étudiant et son niveau. */
    private record Grade(String level, Double on20, boolean excluded) {}

    @GetMapping
    @Transactional(readOnly = true)
    public DashboardDto dashboard() {
        DashboardDto dto = new DashboardDto();
        buildCourses(dto);
        buildExams(dto);
        return dto;
    }

    // ── Cours en ligne et étudiants suivis ─────────────────────────────────

    private void buildCourses(DashboardDto dto) {
        List<Course> courses = courseRepository.findAll();
        Map<Long, List<Progress>> progressByCourse = progressRepository.findAll().stream()
            .filter(p -> p.getCourse() != null && p.getUser() != null && p.getUser().getRole() == Role.ROLE_STUDENT)
            .collect(Collectors.groupingBy(p -> p.getCourse().getId()));

        Map<Long, User> followed = new LinkedHashMap<>();
        Map<Long, Set<Long>> coursesByStudent = new HashMap<>();
        List<CourseReport> reports = new ArrayList<>();

        for (Course course : courses) {
            int lessons = course.getLessons().size();
            List<Progress> progress = progressByCourse.getOrDefault(course.getId(), List.of());
            Map<Long, List<Progress>> byUser = progress.stream().collect(Collectors.groupingBy(p -> p.getUser().getId()));

            Map<Long, User> students = new LinkedHashMap<>();
            userRepository.findByEnrolledCoursesContaining(course).stream()
                .filter(u -> u.getRole() == Role.ROLE_STUDENT)
                .forEach(u -> students.put(u.getId(), u));
            progress.forEach(p -> students.putIfAbsent(p.getUser().getId(), p.getUser()));
            students.forEach((id, u) -> {
                followed.putIfAbsent(id, u);
                coursesByStudent.computeIfAbsent(id, k -> new HashSet<>()).add(course.getId());
            });

            CourseReport r = new CourseReport();
            r.id = course.getId();
            r.title = course.getTitle();
            r.teacher = course.getTeacher() == null ? null : fullName(course.getTeacher());
            r.level = course.getLevel();
            r.category = course.getCategory();
            r.published = course.isPublished();
            r.lessons = lessons;
            r.enrolled = students.size();
            r.active = byUser.size();
            List<Double> percentages = students.keySet().stream()
                .map(id -> completion(byUser.getOrDefault(id, List.of()), lessons)).toList();
            r.completed = (int) percentages.stream().filter(p -> p >= 100).count();
            r.averageProgress = round1(percentages.stream().mapToDouble(Double::doubleValue).average().orElse(0));
            r.timeSpentSeconds = progress.stream().mapToLong(p -> p.getWatchedSeconds() == null ? 0 : p.getWatchedSeconds()).sum();
            reports.add(r);
        }

        CourseStats stats = new CourseStats();
        stats.total = courses.size();
        stats.published = (int) courses.stream().filter(Course::isPublished).count();
        stats.lessons = courses.stream().mapToInt(c -> c.getLessons().size()).sum();
        stats.enrolledStudents = followed.size();
        stats.activeStudents = (int) progressByCourse.values().stream().flatMap(List::stream)
            .map(p -> p.getUser().getId()).distinct().count();
        stats.averageProgress = round1(reports.stream().filter(r -> r.enrolled > 0)
            .mapToDouble(r -> r.averageProgress).average().orElse(0));
        stats.timeSpentSeconds = reports.stream().mapToLong(r -> r.timeSpentSeconds).sum();
        dto.courses = stats;
        dto.courseReports = reports.stream()
            .sorted(Comparator.comparingInt((CourseReport r) -> r.enrolled).reversed()
                .thenComparing(r -> r.title == null ? "" : r.title.toLowerCase()))
            .toList();

        Map<Long, Course> courseById = courses.stream().collect(Collectors.toMap(Course::getId, c -> c));
        dto.followedStudents = followed.values().stream().map(u -> {
            Set<Long> ids = coursesByStudent.getOrDefault(u.getId(), Set.of());
            List<Progress> list = ids.stream()
                .flatMap(cid -> progressByCourse.getOrDefault(cid, List.of()).stream())
                .filter(p -> p.getUser().getId().equals(u.getId())).toList();
            FollowedStudent s = new FollowedStudent();
            s.id = u.getId(); s.firstName = u.getFirstName(); s.lastName = u.getLastName();
            s.email = u.getEmail(); s.level = u.getLevel();
            s.courses = ids.size();
            s.completedLessons = (int) list.stream().filter(Progress::isCompleted).count();
            s.averageProgress = round1(ids.stream().mapToDouble(cid -> completion(
                list.stream().filter(p -> p.getCourse().getId().equals(cid)).toList(),
                courseById.get(cid).getLessons().size())).average().orElse(0));
            s.timeSpentSeconds = list.stream().mapToLong(p -> p.getWatchedSeconds() == null ? 0 : p.getWatchedSeconds()).sum();
            s.lastActivity = list.stream().map(Progress::getLastUpdated).filter(Objects::nonNull)
                .max(LocalDateTime::compareTo).map(LocalDateTime::toString).orElse(null);
            return s;
        }).sorted(Comparator.comparing((FollowedStudent s) -> s.lastActivity == null ? "" : s.lastActivity).reversed())
            .toList();
    }

    /** Pourcentage de leçons terminées d'un cours par un étudiant. */
    private static double completion(List<Progress> progress, int lessons) {
        if (lessons == 0) return 0;
        long done = progress.stream().filter(p -> p.getLesson() != null && p.isCompleted())
            .map(p -> p.getLesson().getId()).distinct().count();
        return Math.min(100, done * 100.0 / lessons);
    }

    // ── Devoirs, examens et rapport par niveau ─────────────────────────────

    private void buildExams(DashboardDto dto) {
        Map<String, String> levelByEmail = userRepository.findAll().stream()
            .filter(u -> u.getEmail() != null && u.getLevel() != null && !u.getLevel().isBlank())
            .collect(Collectors.toMap(u -> u.getEmail().toLowerCase(), User::getLevel, (a, b) -> a));

        List<ExamReport> reports = new ArrayList<>();
        List<Grade> grades = new ArrayList<>();
        Map<String, Integer> assignedByLevel = new HashMap<>();
        Map<String, Set<String>> evaluationsByLevel = new HashMap<>();

        List<Qcm> qcms = qcmRepository.findAll();
        for (Qcm qcm : qcms) {
            String key = "D" + qcm.getId();
            Map<String, String> assignedLevel = new HashMap<>();
            List<QcmStudent> audience = audienceService.audience(qcm);
            for (QcmStudent qs : audience) {
                String email = qs.getStudentEmail() == null ? "" : qs.getStudentEmail().toLowerCase();
                String level = level(qs.getLevel(), levelByEmail.get(email));
                assignedLevel.put(email, level);
                assignedByLevel.merge(level, 1, Integer::sum);
                evaluationsByLevel.computeIfAbsent(level, k -> new HashSet<>()).add(key);
            }
            List<Grade> qcmGrades = new ArrayList<>();
            for (QcmPassage p : passageRepository.findByQcmAndIsSubmittedTrue(qcm)) {
                String email = p.getStudent() == null ? "" : p.getStudent().getEmail().toLowerCase();
                String level = level(p.getDeclaredLevel(), assignedLevel.get(email),
                    p.getStudent() == null ? null : p.getStudent().getLevel());
                if (!assignedLevel.containsKey(email)) {
                    evaluationsByLevel.computeIfAbsent(level, k -> new HashSet<>()).add(key);
                }
                qcmGrades.add(new Grade(level, on20(p.getScore(), p.getMaxScore()), Boolean.TRUE.equals(p.getExcludedForViolations())));
            }
            grades.addAll(qcmGrades);
            reports.add(report(qcm.getId(), "Devoir", qcm.getTitle(), qcm.getProfessor(), qcm.getStatus(),
                qcm.getCreatedAt(), Math.max(audience.size(), qcmGrades.size()), qcmGrades));
        }

        List<Exam> exams = examRepository.findAll();
        for (Exam exam : exams) {
            String key = "E" + exam.getId();
            List<Grade> examGrades = new ArrayList<>();
            for (ExamStudent es : exam.getStudents()) {
                String level = level(levelByEmail.get(es.getStudentEmail() == null ? "" : es.getStudentEmail().toLowerCase()));
                assignedByLevel.merge(level, 1, Integer::sum);
                evaluationsByLevel.computeIfAbsent(level, k -> new HashSet<>()).add(key);
                ExamSubmission sub = es.getSubmission();
                if (sub != null && sub.getSubmittedAt() != null) {
                    Double note = Boolean.TRUE.equals(sub.getIsGraded()) ? on20(sub.getTotalScore(), sub.getMaxScore()) : null;
                    examGrades.add(new Grade(level, note, Boolean.TRUE.equals(es.getExcludedForViolations())));
                }
            }
            grades.addAll(examGrades);
            reports.add(report(exam.getId(), "Examen", exam.getTitle(), exam.getProfessor(),
                exam.getStatus() == null ? null : exam.getStatus().name(), exam.getCreatedAt(),
                exam.getStudents().size(), examGrades));
        }

        ExamStats stats = new ExamStats();
        stats.devoirs = qcms.size();
        stats.devoirsPublished = (int) qcms.stream().filter(q -> "PUBLISHED".equals(q.getStatus())).count();
        stats.examens = exams.size();
        stats.examensPublished = (int) exams.stream().filter(e -> e.getStatus() == ExamStatus.PUBLISHED).count();
        stats.expectedCopies = reports.stream().mapToInt(r -> r.assigned).sum();
        stats.submittedCopies = grades.size();
        stats.averageOn20 = average(grades);
        stats.passRate = passRate(grades);
        dto.exams = stats;
        dto.examReports = reports.stream()
            .sorted(Comparator.comparing((ExamReport r) -> r.createdAt == null ? "" : r.createdAt).reversed())
            .toList();

        Set<String> levels = new TreeSet<>(Comparator.comparing((String l) -> NO_LEVEL.equals(l)).thenComparing(l -> l));
        levels.addAll(assignedByLevel.keySet());
        grades.forEach(g -> levels.add(g.level()));
        dto.levelReports = levels.stream().map(level -> {
            List<Grade> list = grades.stream().filter(g -> g.level().equals(level)).toList();
            List<Double> notes = list.stream().map(Grade::on20).filter(Objects::nonNull).toList();
            LevelReport r = new LevelReport();
            r.level = level;
            r.evaluations = evaluationsByLevel.getOrDefault(level, Set.of()).size();
            r.submitted = list.size();
            r.assigned = Math.max(assignedByLevel.getOrDefault(level, 0), r.submitted);
            r.excluded = (int) list.stream().filter(Grade::excluded).count();
            r.averageOn20 = average(list);
            r.passRate = passRate(list);
            r.best = notes.stream().max(Double::compare).orElse(null);
            r.worst = notes.stream().min(Double::compare).orElse(null);
            return r;
        }).toList();
    }

    private ExamReport report(Long id, String type, String title, User teacher, String status,
                              LocalDateTime createdAt, int assigned, List<Grade> grades) {
        ExamReport r = new ExamReport();
        r.id = id; r.type = type; r.title = title; r.status = status;
        r.teacher = teacher == null ? null : fullName(teacher);
        r.createdAt = createdAt == null ? null : createdAt.toString();
        r.assigned = assigned;
        r.submitted = grades.size();
        r.averageOn20 = average(grades);
        r.passRate = passRate(grades);
        return r;
    }

    /** Premier niveau renseigné, normalisé (L1, M2…), sinon « Non renseigné ». */
    private static String level(String... candidates) {
        for (String c : candidates) {
            if (c != null && !c.isBlank()) return c.trim().toUpperCase();
        }
        return NO_LEVEL;
    }

    private static Double on20(Number score, Number max) {
        if (score == null || max == null || max.doubleValue() <= 0) return null;
        return round1(score.doubleValue() * 20 / max.doubleValue());
    }

    private static Double average(List<Grade> grades) {
        OptionalDouble avg = grades.stream().map(Grade::on20).filter(Objects::nonNull).mapToDouble(Double::doubleValue).average();
        return avg.isPresent() ? round1(avg.getAsDouble()) : null;
    }

    /** Part des copies notées ayant au moins 10/20. */
    private static Double passRate(List<Grade> grades) {
        List<Double> notes = grades.stream().map(Grade::on20).filter(Objects::nonNull).toList();
        if (notes.isEmpty()) return null;
        return round1(notes.stream().filter(n -> n >= 10).count() * 100.0 / notes.size());
    }

    private static double round1(double v) { return Math.round(v * 10) / 10.0; }

    private static String fullName(User u) {
        return ((u.getFirstName() == null ? "" : u.getFirstName()) + " " + (u.getLastName() == null ? "" : u.getLastName())).trim();
    }
}
