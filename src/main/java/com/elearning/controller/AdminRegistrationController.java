package com.elearning.controller;

import com.elearning.entity.User;
import com.elearning.repository.UserRepository;
import com.elearning.service.EmailService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/registrations")
@RequiredArgsConstructor
public class AdminRegistrationController {

    private final UserRepository userRepository;
    private final EmailService emailService;

    @Data
    static class RegistrationDto {
        Long id; String firstName; String lastName; String email;
        String specialization; String paymentMethod; String paymentPhone;
        String paymentReference; LocalDateTime paymentSubmittedAt;
        String registrationStatus; LocalDateTime createdAt;

        static RegistrationDto from(User u) {
            RegistrationDto d = new RegistrationDto();
            d.id = u.getId(); d.firstName = u.getFirstName(); d.lastName = u.getLastName();
            d.email = u.getEmail(); d.specialization = u.getSpecialization();
            d.paymentMethod = u.getPaymentMethod(); d.paymentPhone = u.getPaymentPhone();
            d.paymentReference = u.getPaymentReference(); d.paymentSubmittedAt = u.getPaymentSubmittedAt();
            d.registrationStatus = u.getRegistrationStatus(); d.createdAt = u.getCreatedAt();
            return d;
        }
    }

    @GetMapping
    public ResponseEntity<List<RegistrationDto>> getPendingRegistrations() {
        List<RegistrationDto> result = userRepository
            .findByRegistrationStatusOrderByCreatedAtDesc("PAYMENT_SUBMITTED")
            .stream().map(RegistrationDto::from).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/count")
    public ResponseEntity<Map<String, Long>> getPendingCount() {
        long count = userRepository.countByRegistrationStatus("PAYMENT_SUBMITTED");
        return ResponseEntity.ok(Map.of("count", count));
    }

    @PostMapping("/{id}/validate")
    @Transactional
    public ResponseEntity<Map<String, String>> validate(@PathVariable Long id) {
        User user = userRepository.findById(id).orElseThrow();
        user.setEnabled(true);
        user.setRegistrationStatus("APPROVED");
        userRepository.save(user);
        emailService.sendRegistrationApproved(user.getEmail(), user.getFirstName());
        return ResponseEntity.ok(Map.of("message", "Compte validé et étudiant notifié."));
    }

    @PostMapping("/{id}/reject")
    @Transactional
    public ResponseEntity<Map<String, String>> reject(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        User user = userRepository.findById(id).orElseThrow();
        user.setRegistrationStatus("REJECTED");
        userRepository.save(user);
        String reason = body != null ? body.getOrDefault("reason", "") : "";
        emailService.sendRegistrationRejected(user.getEmail(), user.getFirstName(), reason);
        return ResponseEntity.ok(Map.of("message", "Demande rejetée et étudiant notifié."));
    }
}
