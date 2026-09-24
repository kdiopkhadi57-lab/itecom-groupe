package com.elearning.controller;

import com.elearning.entity.Role;
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
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUsersController {

    private final UserRepository userRepository;
    private final EmailService emailService;

    @Data
    static class UserDto {
        Long id; String firstName; String lastName; String email;
        String specialization; String bio; String avatarUrl;
        String role; String registrationStatus; boolean enabled;
        LocalDateTime createdAt;

        static UserDto from(User u) {
            UserDto d = new UserDto();
            d.id = u.getId(); d.firstName = u.getFirstName(); d.lastName = u.getLastName();
            d.email = u.getEmail(); d.specialization = u.getSpecialization();
            d.bio = u.getBio(); d.avatarUrl = u.getAvatarUrl();
            d.role = u.getRole() != null ? u.getRole().name() : null;
            d.registrationStatus = u.getRegistrationStatus();
            d.enabled = u.isEnabled(); d.createdAt = u.getCreatedAt();
            return d;
        }
    }

    @GetMapping("/students")
    public ResponseEntity<List<UserDto>> getStudents() {
        List<UserDto> result = userRepository
            .findByRoleAndRegistrationStatusOrderByCreatedAtDesc(Role.ROLE_STUDENT, "APPROVED")
            .stream().map(UserDto::from).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/teachers")
    public ResponseEntity<List<UserDto>> getTeachers() {
        List<UserDto> result = userRepository
            .findByRoleAndRegistrationStatusOrderByCreatedAtDesc(Role.ROLE_TEACHER, "APPROVED")
            .stream().map(UserDto::from).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> getStats() {
        long students = userRepository.countByRoleAndRegistrationStatus(Role.ROLE_STUDENT, "APPROVED");
        long teachers = userRepository.countByRoleAndRegistrationStatus(Role.ROLE_TEACHER, "APPROVED");
        long pending  = userRepository.countByRegistrationStatus("PAYMENT_SUBMITTED");
        return ResponseEntity.ok(Map.of("students", students, "teachers", teachers, "pending", pending));
    }

    @PostMapping("/{id}/disable")
    @Transactional
    public ResponseEntity<Map<String, String>> disable(@PathVariable Long id) {
        User user = userRepository.findById(id).orElseThrow();
        user.setEnabled(false);
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "Compte désactivé."));
    }

    @PostMapping("/{id}/enable")
    @Transactional
    public ResponseEntity<Map<String, String>> enable(@PathVariable Long id) {
        User user = userRepository.findById(id).orElseThrow();
        user.setEnabled(true);
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "Compte réactivé."));
    }
}
