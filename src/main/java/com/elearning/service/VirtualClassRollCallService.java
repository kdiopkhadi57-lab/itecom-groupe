package com.elearning.service;

import com.elearning.entity.*;
import com.elearning.repository.VirtualClassAttendanceRepository;
import com.elearning.repository.VirtualClassRollCallAnswerRepository;
import com.elearning.repository.VirtualClassRollCallRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Appel en classe virtuelle : le professeur le lance (rappel toutes les 15 minutes), chaque étudiant connecté
 * reçoit « Je suis présent » et a {@link #WINDOW_MINUTES} minutes pour répondre.
 */
@Service
@RequiredArgsConstructor
public class VirtualClassRollCallService {

    public static final long WINDOW_MINUTES = 3;
    /** Sans battement depuis ce délai, l'étudiant n'est plus considéré comme connecté. */
    private static final long ONLINE_SECONDS = 90;

    private final VirtualClassRollCallRepository rollCallRepository;
    private final VirtualClassRollCallAnswerRepository answerRepository;
    private final VirtualClassAttendanceRepository attendanceRepository;
    private final StudentAudienceService audienceService;

    public record StudentLine(String name, String email, String status, LocalDateTime answeredAt) {}   // PRESENT | NO_ANSWER | NOT_CONNECTED

    public record RollCallView(Long id, LocalDateTime startedAt, LocalDateTime expiresAt, boolean open,
                               int expected, int present, int noAnswer, int notConnected, List<StudentLine> students) {}

    public record ActiveCall(Long id, LocalDateTime expiresAt, long secondsLeft) {}

    @Transactional
    public RollCallView start(VirtualClass vc, String startedBy) {
        LocalDateTime now = LocalDateTime.now();
        // Un seul appel ouvert à la fois : le précédent est clos
        rollCallRepository.findByVirtualClassOrderByStartedAtAsc(vc).stream()
            .filter(r -> r.getExpiresAt().isAfter(now)).forEach(r -> r.setExpiresAt(now));
        VirtualClassRollCall call = rollCallRepository.save(VirtualClassRollCall.builder()
            .virtualClass(vc).startedAt(now).expiresAt(now.plusMinutes(WINDOW_MINUTES)).startedBy(startedBy).build());
        return view(call);
    }

    public Optional<VirtualClassRollCall> find(VirtualClass vc, Long rollCallId) {
        return rollCallRepository.findById(rollCallId).filter(r -> r.getVirtualClass().getId().equals(vc.getId()));
    }

    /** Appel ouvert auquel cet utilisateur n'a pas encore répondu. */
    public Optional<ActiveCall> activeFor(VirtualClass vc, String email) {
        LocalDateTime now = LocalDateTime.now();
        return rollCallRepository.findByVirtualClassOrderByStartedAtAsc(vc).stream()
            .filter(r -> r.getExpiresAt().isAfter(now))
            .filter(r -> !answerRepository.existsByRollCallAndEmailIgnoreCase(r, email))
            .reduce((a, b) -> b)
            .map(r -> new ActiveCall(r.getId(), r.getExpiresAt(), Duration.between(now, r.getExpiresAt()).getSeconds()));
    }

    /** Enregistre « Je suis présent » ; refusé après la fin de l'appel. */
    @Transactional
    public void answer(VirtualClassRollCall call, User user) {
        if (!call.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("L'appel est terminé : votre réponse est arrivée trop tard.");
        }
        if (answerRepository.existsByRollCallAndEmailIgnoreCase(call, user.getEmail())) return;
        answerRepository.save(VirtualClassRollCallAnswer.builder().rollCall(call).email(user.getEmail().toLowerCase())
            .fullName((user.getFirstName() + " " + user.getLastName()).trim()).answeredAt(LocalDateTime.now()).build());
    }

    /** Étudiants attendus : présents (ont répondu), connectés sans réponse, non connectés. */
    public RollCallView view(VirtualClassRollCall call) {
        VirtualClass vc = call.getVirtualClass();
        LocalDateTime now = LocalDateTime.now();
        Map<String, LocalDateTime> answers = new HashMap<>();
        Map<String, String> answerNames = new LinkedHashMap<>();
        for (VirtualClassRollCallAnswer a : answerRepository.findByRollCall(call)) {
            answers.put(a.getEmail().toLowerCase(), a.getAnsweredAt());
            answerNames.put(a.getEmail().toLowerCase(), a.getFullName());
        }
        Set<String> online = new HashSet<>();
        for (VirtualClassAttendance a : attendanceRepository.findByVirtualClassOrderByJoinedAtAsc(vc)) {
            if (a.getRole() == Role.ROLE_STUDENT && a.getLeftAt() == null && a.getLastSeenAt() != null
                && Duration.between(a.getLastSeenAt(), now).getSeconds() <= ONLINE_SECONDS) {
                online.add(a.getEmail().toLowerCase());
            }
        }
        // Étudiants de la séance, puis ceux qui ont répondu ou sont connectés sans y être inscrits
        LinkedHashMap<String, String> expected = audienceService.virtualClassAudience(vc);
        answerNames.forEach(expected::putIfAbsent);
        for (VirtualClassAttendance a : attendanceRepository.findByVirtualClassOrderByJoinedAtAsc(vc)) {
            if (online.contains(a.getEmail().toLowerCase())) expected.putIfAbsent(a.getEmail().toLowerCase(), a.getFullName());
        }
        List<StudentLine> lines = new ArrayList<>();
        expected.forEach((email, name) -> {
            String status = answers.containsKey(email) ? "PRESENT" : online.contains(email) ? "NO_ANSWER" : "NOT_CONNECTED";
            lines.add(new StudentLine(name, email, status, answers.get(email)));
        });
        Map<String, Integer> order = Map.of("NO_ANSWER", 0, "NOT_CONNECTED", 1, "PRESENT", 2);
        lines.sort(Comparator.comparing((StudentLine l) -> order.get(l.status()))
            .thenComparing(l -> l.name() == null ? "" : l.name().toLowerCase()));
        int present = (int) lines.stream().filter(l -> "PRESENT".equals(l.status())).count();
        int noAnswer = (int) lines.stream().filter(l -> "NO_ANSWER".equals(l.status())).count();
        return new RollCallView(call.getId(), call.getStartedAt(), call.getExpiresAt(), call.getExpiresAt().isAfter(now),
            lines.size(), present, noAnswer, lines.size() - present - noAnswer, lines);
    }

    public List<VirtualClassRollCall> rollCalls(VirtualClass vc) {
        return rollCallRepository.findByVirtualClassOrderByStartedAtAsc(vc);
    }

    /** Adresses ayant répondu, par appel (pour le rapport de présence). */
    public Map<Long, Set<String>> answeredEmails(List<VirtualClassRollCall> calls) {
        Map<Long, Set<String>> result = new HashMap<>();
        calls.forEach(c -> result.put(c.getId(), new HashSet<>()));
        if (calls.isEmpty()) return result;
        for (VirtualClassRollCallAnswer a : answerRepository.findByRollCallIn(calls)) {
            result.get(a.getRollCall().getId()).add(a.getEmail().toLowerCase());
        }
        return result;
    }
}
