package com.elearning.controller;

import com.elearning.entity.User;
import com.elearning.entity.VirtualClass;
import com.elearning.repository.UserRepository;
import com.elearning.repository.VirtualClassRepository;
import com.elearning.service.VirtualClassRollCallService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Appel en classe virtuelle : lancé par le professeur, confirmé par chaque étudiant connecté. */
@RestController
@RequiredArgsConstructor
public class VirtualClassRollCallController {

    private final VirtualClassRepository virtualClassRepository;
    private final UserRepository userRepository;
    private final VirtualClassRollCallService rollCallService;

    /** Le professeur de la séance (ou l'admin) lance un appel. */
    @PostMapping("/api/teacher/virtual-classes/{id}/roll-calls")
    public ResponseEntity<?> start(@PathVariable Long id, Authentication auth) {
        VirtualClass vc = virtualClassRepository.findById(id).orElse(null);
        if (vc == null) return ResponseEntity.notFound().build();
        if (!VirtualClassAttendanceController.canSeeReport(vc, auth)) return forbidden();
        return ResponseEntity.ok(rollCallService.start(vc, auth.getName()));
    }

    /** Suivi en direct des réponses. */
    @GetMapping("/api/teacher/virtual-classes/{id}/roll-calls/{rollCallId}")
    public ResponseEntity<?> status(@PathVariable Long id, @PathVariable Long rollCallId, Authentication auth) {
        VirtualClass vc = virtualClassRepository.findById(id).orElse(null);
        if (vc == null) return ResponseEntity.notFound().build();
        if (!VirtualClassAttendanceController.canSeeReport(vc, auth)) return forbidden();
        return rollCallService.find(vc, rollCallId).<ResponseEntity<?>>map(c -> ResponseEntity.ok(rollCallService.view(c)))
            .orElse(ResponseEntity.notFound().build());
    }

    /** Côté étudiant : y a-t-il un appel auquel répondre ? (204 sinon) */
    @GetMapping("/api/virtual-classes/{id}/roll-calls/active")
    public ResponseEntity<?> active(@PathVariable Long id, Authentication auth) {
        VirtualClass vc = virtualClassRepository.findById(id).orElse(null);
        if (vc == null) return ResponseEntity.notFound().build();
        return rollCallService.activeFor(vc, auth.getName()).<ResponseEntity<?>>map(ResponseEntity::ok)
            .orElse(ResponseEntity.noContent().build());
    }

    @PostMapping("/api/virtual-classes/{id}/roll-calls/{rollCallId}/answer")
    public ResponseEntity<?> answer(@PathVariable Long id, @PathVariable Long rollCallId, Authentication auth) {
        VirtualClass vc = virtualClassRepository.findById(id).orElse(null);
        if (vc == null) return ResponseEntity.notFound().build();
        User user = userRepository.findByEmail(auth.getName()).orElseThrow();
        if (!VirtualClassAttendanceController.canJoin(vc, user)) return forbidden();
        var call = rollCallService.find(vc, rollCallId).orElse(null);
        if (call == null) return ResponseEntity.notFound().build();
        rollCallService.answer(call, user);
        return ResponseEntity.ok(Map.of("message", "Présence confirmée."));
    }

    private static ResponseEntity<?> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(Map.of("message", "Seuls le professeur de la séance et l'administrateur peuvent faire l'appel."));
    }
}
