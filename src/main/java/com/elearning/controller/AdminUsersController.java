package com.elearning.controller;

import com.elearning.entity.Role;
import com.elearning.entity.User;
import com.elearning.repository.UserRepository;
import com.elearning.service.EmailService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
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
    private final PasswordEncoder passwordEncoder;

    @Data
    static class CreateUserInput {
        String firstName; String lastName; String email;
        String role;            // STUDENT | TEACHER
        String specialization;
        String password;        // généré s'il est vide
    }

    // Sans caractères ambigus (0/O, 1/l/I) pour faciliter la saisie
    private static final String PASSWORD_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private static String generatePassword() {
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) sb.append(PASSWORD_ALPHABET.charAt(RANDOM.nextInt(PASSWORD_ALPHABET.length())));
        return sb.toString();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** Création d'un compte étudiant ou professeur par l'administrateur ; identifiants envoyés par email. */
    @PostMapping
    @Transactional
    public ResponseEntity<?> create(@RequestBody CreateUserInput input) {
        if (isBlank(input.firstName) || isBlank(input.lastName) || isBlank(input.email)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Prénom, nom et email sont obligatoires."));
        }
        String email = input.email.trim().toLowerCase();
        if (!email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            return ResponseEntity.badRequest().body(Map.of("message", "Adresse email invalide."));
        }
        if (userRepository.findByEmail(email).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Un compte existe déjà avec l'email " + email + "."));
        }
        Role role = "TEACHER".equalsIgnoreCase(input.role) ? Role.ROLE_TEACHER : Role.ROLE_STUDENT;
        String password = isBlank(input.password) ? generatePassword() : input.password.trim();
        if (password.length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("message", "Le mot de passe doit contenir au moins 6 caractères."));
        }

        User user = userRepository.save(User.builder()
            .firstName(input.firstName.trim()).lastName(input.lastName.trim())
            .email(email)
            .password(passwordEncoder.encode(password))
            .role(role)
            .specialization(isBlank(input.specialization) ? null : input.specialization.trim())
            .enabled(true)
            .registrationStatus("APPROVED")
            .build());
        String emailError = emailService.sendAccountCreated(email, user.getFirstName(),
            role == Role.ROLE_TEACHER ? "professeur" : "étudiant", password);
        boolean emailSent = emailError == null;

        return ResponseEntity.ok(Map.of(
            "user", UserDto.from(user),
            "emailSent", emailSent,
            "message", emailSent
                ? "Compte créé. Les identifiants ont été envoyés à " + email + "."
                : "Compte créé, mais l'email n'a pas pu être envoyé : " + emailError
                    + ". Communiquez les identifiants manuellement (mot de passe : " + password + ").",
            "password", emailSent ? "" : password));
    }

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
