package com.elearning.controller;

import com.elearning.dto.request.LoginRequest;
import com.elearning.dto.request.RegisterRequest;
import com.elearning.dto.response.ApiResponse;
import com.elearning.dto.response.AuthResponse;
import com.elearning.entity.User;
import com.elearning.repository.UserRepository;
import com.elearning.service.AuthService;
import com.elearning.service.EmailService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;
    private final EmailService emailService;

    @Value("${app.admin.email}")
    private String adminEmail;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<String>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/verify-email")
    public ResponseEntity<ApiResponse<String>> verifyEmail(@RequestParam String token) {
        return ResponseEntity.ok(authService.verifyEmail(token));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<String>> forgotPassword(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(authService.forgotPassword(body.get("email")));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<String>> resetPassword(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(authService.resetPassword(body.get("token"), body.get("password")));
    }

    @PostMapping("/submit-payment")
    @Transactional
    public ResponseEntity<ApiResponse<String>> submitPayment(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) return ResponseEntity.ok(ApiResponse.error("Utilisateur non trouvé"));
        if (!"PAYMENT_PENDING".equals(user.getRegistrationStatus()) &&
            !"PAYMENT_SUBMITTED".equals(user.getRegistrationStatus())) {
            return ResponseEntity.ok(ApiResponse.error("Statut d'inscription invalide"));
        }
        user.setPaymentMethod(body.get("paymentMethod"));
        user.setPaymentPhone(body.get("paymentPhone"));
        user.setPaymentReference(body.get("paymentReference"));
        user.setPaymentSubmittedAt(LocalDateTime.now());
        user.setRegistrationStatus("PAYMENT_SUBMITTED");
        userRepository.save(user);
        emailService.sendPaymentReceivedToAdmin(adminEmail, user);
        return ResponseEntity.ok(ApiResponse.success("Paiement soumis. L'admin validera votre compte sous 24h.", null));
    }

    @GetMapping("/registration-status")
    public ResponseEntity<Map<String, String>> getRegistrationStatus(@RequestParam String email) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) return ResponseEntity.ok(Map.of("status", "NOT_FOUND"));
        return ResponseEntity.ok(Map.of("status", user.getRegistrationStatus()));
    }
}
