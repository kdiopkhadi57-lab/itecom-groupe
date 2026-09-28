package com.elearning.controller;

import com.elearning.entity.*;
import com.elearning.repository.UserRepository;
import com.elearning.repository.VirtualClassAttendanceRepository;
import com.elearning.repository.VirtualClassRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Présences aux séances de classe virtuelle : enregistrement des connexions
 * (arrivée, battements, départ) et rapport par séance pour le professeur et l'admin.
 */
@RestController
@RequiredArgsConstructor
public class VirtualClassAttendanceController {

    /** Sans battement depuis ce délai, la connexion est considérée comme terminée. */
    private static final long STALE_AFTER_SECONDS = 90;
    /** Tolérance pour « en retard » et « parti avant la fin ». */
    private static final long TOLERANCE_MINUTES = 10;
    private static final DateTimeFormatter FR_DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter FR_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final VirtualClassRepository virtualClassRepository;
    private final VirtualClassAttendanceRepository attendanceRepository;
    private final UserRepository userRepository;

    // ── Enregistrement des connexions ─────────────────────────────────────

    @PostMapping("/api/virtual-classes/{id}/attendance/join")
    @Transactional
    public ResponseEntity<?> join(@PathVariable Long id, Authentication auth) {
        VirtualClass vc = virtualClassRepository.findById(id).orElse(null);
        if (vc == null) return ResponseEntity.notFound().build();
        User user = userRepository.findByEmail(auth.getName()).orElseThrow();
        if (!canJoin(vc, user)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

        LocalDateTime now = LocalDateTime.now();
        // Une connexion restée ouverte (onglet fermé brutalement) est clôturée à son dernier signe de vie
        attendanceRepository.findByVirtualClassOrderByJoinedAtAsc(vc).stream()
            .filter(a -> a.getLeftAt() == null && a.getEmail().equalsIgnoreCase(user.getEmail()))
            .forEach(a -> a.setLeftAt(a.getLastSeenAt() != null ? a.getLastSeenAt() : a.getJoinedAt()));

        VirtualClassAttendance attendance = attendanceRepository.save(VirtualClassAttendance.builder()
            .virtualClass(vc).user(user).email(user.getEmail().toLowerCase())
            .fullName((user.getFirstName() + " " + user.getLastName()).trim())
            .role(user.getRole()).joinedAt(now).lastSeenAt(now)
            .build());
        return ResponseEntity.ok(Map.of("attendanceId", attendance.getId()));
    }

    @PostMapping("/api/virtual-classes/{id}/attendance/{attendanceId}/heartbeat")
    @Transactional
    public ResponseEntity<?> heartbeat(@PathVariable Long id, @PathVariable Long attendanceId, Authentication auth) {
        VirtualClassAttendance a = ownAttendance(id, attendanceId, auth);
        if (a == null) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        if (a.getLeftAt() == null) a.setLastSeenAt(LocalDateTime.now());
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/api/virtual-classes/{id}/attendance/{attendanceId}/leave")
    @Transactional
    public ResponseEntity<?> leave(@PathVariable Long id, @PathVariable Long attendanceId, Authentication auth) {
        VirtualClassAttendance a = ownAttendance(id, attendanceId, auth);
        if (a == null) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        if (a.getLeftAt() == null) {
            LocalDateTime now = LocalDateTime.now();
            a.setLastSeenAt(now);
            a.setLeftAt(now);
        }
        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ── Rapport ───────────────────────────────────────────────────────────

    @Data static class SegmentDto { String joinedAt; String leftAt; long durationSeconds; boolean online; }
    @Data static class ParticipantDto {
        String name; String email; boolean enrolled; String status; // PRESENT, ABSENT
        boolean late; boolean leftEarly; boolean online;
        String firstJoinAt; String lastLeaveAt; long totalSeconds; int connections;
        List<SegmentDto> segments;
    }
    @Data static class ReportDto {
        Long classId; String title; String courseTitle; String teacherName;
        String scheduledAt; Integer durationMinutes; String scheduledEndAt; String status;
        int enrolledCount; int presentCount; int absentCount; int lateCount; int leftEarlyCount;
        double attendanceRate; long averageSeconds;
        ParticipantDto teacher;
        List<ParticipantDto> students;
    }

    @GetMapping("/api/teacher/virtual-classes/{id}/attendance")
    @Transactional(readOnly = true)
    public ResponseEntity<?> report(@PathVariable Long id, Authentication auth) {
        VirtualClass vc = virtualClassRepository.findById(id).orElse(null);
        if (vc == null) return ResponseEntity.notFound().build();
        if (!canSeeReport(vc, auth)) return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(Map.of("message", "Seuls le professeur de la séance et l'administrateur peuvent voir ce rapport."));
        return ResponseEntity.ok(buildReport(vc));
    }

    @GetMapping("/api/teacher/virtual-classes/{id}/attendance.xlsx")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> reportExcel(@PathVariable Long id, Authentication auth) throws IOException {
        VirtualClass vc = virtualClassRepository.findById(id).orElse(null);
        if (vc == null) return ResponseEntity.notFound().build();
        if (!canSeeReport(vc, auth)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        byte[] bytes = toExcel(buildReport(vc));
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"presences-seance-" + id + ".xlsx\"")
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(bytes);
    }

    private ReportDto buildReport(VirtualClass vc) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = vc.getScheduledAt();
        LocalDateTime end = start != null && vc.getDurationMinutes() != null ? start.plusMinutes(vc.getDurationMinutes()) : null;
        List<VirtualClassAttendance> all = attendanceRepository.findByVirtualClassOrderByJoinedAtAsc(vc);

        Map<String, List<VirtualClassAttendance>> byEmail = new LinkedHashMap<>();
        all.forEach(a -> byEmail.computeIfAbsent(a.getEmail().toLowerCase(), k -> new ArrayList<>()).add(a));

        ReportDto r = new ReportDto();
        r.classId = vc.getId();
        r.title = vc.getTitle();
        r.courseTitle = vc.getCourse() != null ? vc.getCourse().getTitle() : null;
        r.teacherName = vc.getTeacher() != null ? (vc.getTeacher().getFirstName() + " " + vc.getTeacher().getLastName()).trim() : null;
        r.scheduledAt = start != null ? start.toString() : null;
        r.durationMinutes = vc.getDurationMinutes();
        r.scheduledEndAt = end != null ? end.toString() : null;
        r.status = vc.getStatus();

        // Étudiants inscrits à la séance, puis étudiants connectés hors liste
        Set<String> listed = new HashSet<>();
        List<ParticipantDto> students = new ArrayList<>();
        for (VirtualClassStudent s : vc.getStudents()) {
            String email = s.getStudentEmail().toLowerCase();
            if (!listed.add(email)) continue;
            students.add(participant(s.getStudentName(), email, true, byEmail.getOrDefault(email, List.of()), start, end, now));
        }
        byEmail.forEach((email, list) -> {
            if (listed.contains(email) || list.get(0).getRole() != Role.ROLE_STUDENT) return;
            students.add(participant(list.get(0).getFullName(), email, false, list, start, end, now));
        });
        students.sort(Comparator.comparing((ParticipantDto p) -> "ABSENT".equals(p.status))
            .thenComparing(p -> p.name == null ? "" : p.name.toLowerCase()));
        r.students = students;

        if (vc.getTeacher() != null) {
            String email = vc.getTeacher().getEmail().toLowerCase();
            r.teacher = participant(r.teacherName, email, true, byEmail.getOrDefault(email, List.of()), start, end, now);
        }

        List<ParticipantDto> enrolled = students.stream().filter(p -> p.enrolled).toList();
        r.enrolledCount = enrolled.size();
        r.presentCount = (int) enrolled.stream().filter(p -> "PRESENT".equals(p.status)).count();
        r.absentCount = r.enrolledCount - r.presentCount;
        r.lateCount = (int) students.stream().filter(p -> p.late).count();
        r.leftEarlyCount = (int) students.stream().filter(p -> p.leftEarly).count();
        r.attendanceRate = r.enrolledCount > 0 ? Math.round(r.presentCount * 1000.0 / r.enrolledCount) / 10.0 : 0;
        List<ParticipantDto> present = students.stream().filter(p -> "PRESENT".equals(p.status)).toList();
        r.averageSeconds = present.isEmpty() ? 0 : Math.round(present.stream().mapToLong(p -> p.totalSeconds).average().orElse(0));
        return r;
    }

    private ParticipantDto participant(String name, String email, boolean enrolled, List<VirtualClassAttendance> list,
                                       LocalDateTime start, LocalDateTime end, LocalDateTime now) {
        ParticipantDto p = new ParticipantDto();
        p.name = name; p.email = email; p.enrolled = enrolled;
        p.connections = list.size();
        p.segments = new ArrayList<>();

        List<LocalDateTime[]> intervals = new ArrayList<>();
        for (VirtualClassAttendance a : list) {
            boolean online = a.getLeftAt() == null && a.getLastSeenAt() != null
                && Duration.between(a.getLastSeenAt(), now).getSeconds() <= STALE_AFTER_SECONDS;
            LocalDateTime segEnd = a.getLeftAt() != null ? a.getLeftAt()
                : online ? now : (a.getLastSeenAt() != null ? a.getLastSeenAt() : a.getJoinedAt());
            SegmentDto s = new SegmentDto();
            s.joinedAt = a.getJoinedAt().toString();
            s.leftAt = online ? null : segEnd.toString();
            s.online = online;
            s.durationSeconds = Math.max(0, Duration.between(a.getJoinedAt(), segEnd).getSeconds());
            p.segments.add(s);
            intervals.add(new LocalDateTime[]{a.getJoinedAt(), segEnd});
            if (online) p.online = true;
        }

        if (list.isEmpty()) {
            p.status = "ABSENT";
            return p;
        }
        p.status = "PRESENT";
        // Durée réelle : fusion des connexions qui se chevauchent (plusieurs onglets)
        intervals.sort(Comparator.comparing(i -> i[0]));
        long total = 0;
        LocalDateTime curStart = null, curEnd = null;
        for (LocalDateTime[] i : intervals) {
            if (curEnd == null || i[0].isAfter(curEnd)) {
                if (curEnd != null) total += Duration.between(curStart, curEnd).getSeconds();
                curStart = i[0]; curEnd = i[1];
            } else if (i[1].isAfter(curEnd)) {
                curEnd = i[1];
            }
        }
        total += Duration.between(curStart, curEnd).getSeconds();
        p.totalSeconds = Math.max(0, total);

        LocalDateTime firstJoin = intervals.get(0)[0];
        LocalDateTime lastLeave = intervals.stream().map(i -> i[1]).max(LocalDateTime::compareTo).orElse(firstJoin);
        p.firstJoinAt = firstJoin.toString();
        p.lastLeaveAt = p.online ? null : lastLeave.toString();
        p.late = start != null && firstJoin.isAfter(start.plusMinutes(TOLERANCE_MINUTES));
        p.leftEarly = !p.online && end != null && now.isAfter(end) && lastLeave.isBefore(end.minusMinutes(TOLERANCE_MINUTES));
        return p;
    }

    private byte[] toExcel(ReportDto r) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Présences");
            CellStyle bold = wb.createCellStyle();
            Font boldFont = wb.createFont(); boldFont.setBold(true); bold.setFont(boldFont);
            CellStyle header = wb.createCellStyle();
            header.setFont(boldFont);
            header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            int row = 0;
            String[][] info = {
                {"Séance", r.title},
                {"Cours", r.courseTitle != null ? r.courseTitle : "—"},
                {"Professeur", r.teacherName != null ? r.teacherName : "—"},
                {"Prévue le", fmtDateTime(r.scheduledAt)},
                {"Durée prévue", r.durationMinutes != null ? r.durationMinutes + " min" : "—"},
                {"Présents", r.presentCount + " / " + r.enrolledCount + " (" + r.attendanceRate + " %)"},
                {"Absents", String.valueOf(r.absentCount)},
                {"En retard", String.valueOf(r.lateCount)},
                {"Partis avant la fin", String.valueOf(r.leftEarlyCount)},
                {"Professeur connecté", r.teacher == null || "ABSENT".equals(r.teacher.status) ? "Non"
                    : "De " + fmtTime(r.teacher.firstJoinAt) + " à " + (r.teacher.online ? "maintenant" : fmtTime(r.teacher.lastLeaveAt))},
            };
            for (String[] line : info) {
                Row x = sheet.createRow(row++);
                Cell k = x.createCell(0); k.setCellValue(line[0]); k.setCellStyle(bold);
                x.createCell(1).setCellValue(line[1]);
            }
            row++;

            String[] headers = {"Étudiant", "Email", "Inscrit à la séance", "Statut", "Retard", "Parti avant la fin",
                "Première connexion", "Dernier départ", "Durée connectée (min)", "Connexions", "Détail des connexions"};
            Row h = sheet.createRow(row++);
            for (int i = 0; i < headers.length; i++) { Cell c = h.createCell(i); c.setCellValue(headers[i]); c.setCellStyle(header); }

            for (ParticipantDto p : r.students) {
                Row x = sheet.createRow(row++);
                x.createCell(0).setCellValue(p.name != null ? p.name : "");
                x.createCell(1).setCellValue(p.email);
                x.createCell(2).setCellValue(p.enrolled ? "Oui" : "Non");
                x.createCell(3).setCellValue("PRESENT".equals(p.status) ? (p.online ? "Présent (en ligne)" : "Présent") : "Absent");
                x.createCell(4).setCellValue(p.late ? "Oui" : "");
                x.createCell(5).setCellValue(p.leftEarly ? "Oui" : "");
                x.createCell(6).setCellValue(fmtDateTime(p.firstJoinAt));
                x.createCell(7).setCellValue(p.online ? "En ligne" : fmtDateTime(p.lastLeaveAt));
                x.createCell(8).setCellValue(Math.round(p.totalSeconds / 6.0) / 10.0);
                x.createCell(9).setCellValue(p.connections);
                StringBuilder detail = new StringBuilder();
                for (SegmentDto s : p.segments) {
                    if (detail.length() > 0) detail.append(" ; ");
                    detail.append(fmtTime(s.joinedAt)).append(" → ").append(s.online ? "en ligne" : fmtTime(s.leftAt));
                }
                x.createCell(10).setCellValue(detail.toString());
            }
            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
            wb.write(out);
            return out.toByteArray();
        }
    }

    // ── Droits ────────────────────────────────────────────────────────────

    private boolean canJoin(VirtualClass vc, User user) {
        boolean host = user.getRole() == Role.ROLE_ADMIN || user.getRole() == Role.ROLE_TEACHER;
        boolean assigned = vc.getStudents().stream().anyMatch(s -> s.getStudentEmail().equalsIgnoreCase(user.getEmail()));
        return host || assigned;
    }

    private boolean canSeeReport(VirtualClass vc, Authentication auth) {
        boolean admin = auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        boolean owner = vc.getTeacher() != null && vc.getTeacher().getEmail().equalsIgnoreCase(auth.getName());
        return admin || owner;
    }

    private VirtualClassAttendance ownAttendance(Long classId, Long attendanceId, Authentication auth) {
        return attendanceRepository.findById(attendanceId)
            .filter(a -> a.getVirtualClass().getId().equals(classId) && a.getEmail().equalsIgnoreCase(auth.getName()))
            .orElse(null);
    }

    private static String fmtDateTime(String iso) {
        return iso == null ? "—" : LocalDateTime.parse(iso).format(FR_DATE_TIME);
    }

    private static String fmtTime(String iso) {
        return iso == null ? "—" : LocalDateTime.parse(iso).format(FR_TIME);
    }
}
