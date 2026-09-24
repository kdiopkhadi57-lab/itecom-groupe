package com.elearning.service.impl;

import com.elearning.dto.request.LoginRequest;
import com.elearning.dto.request.RegisterRequest;
import com.elearning.dto.response.*;
import com.elearning.entity.User;
import com.elearning.exception.AccountBlockedException;
import com.elearning.repository.UserRepository;
import com.elearning.security.jwt.JwtUtils;
import com.elearning.service.AuthService;
import com.elearning.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;

    @Value("${app.admin.email}")
    private String adminEmail;

    @Override
    @Transactional
    public ApiResponse<String> register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            return ApiResponse.error("Email déjà utilisé");
        }

        User user = User.builder()
            .firstName(request.getFirstName())
            .lastName(request.getLastName())
            .email(request.getEmail())
            .password(passwordEncoder.encode(request.getPassword()))
            .role(request.getRole())
            .specialization(request.getSpecialization())
            .enabled(false)
            .registrationStatus("PAYMENT_SUBMITTED")
            .paymentMethod(request.getPaymentMethod())
            .paymentPhone(request.getPaymentPhone())
            .paymentReference(request.getPaymentReference())
            .paymentSubmittedAt(LocalDateTime.now())
            .build();

        userRepository.save(user);
        emailService.sendPaymentReceivedToAdmin(adminEmail, user);

        return ApiResponse.success("Demande soumise. L'administrateur validera votre compte sous 24h.", null);
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        Authentication auth = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

        User user = userRepository.findByEmail(request.getEmail())
            .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        if (user.getBlockedUntil() != null) {
            if (user.getBlockedUntil().isAfter(LocalDateTime.now())) {
                throw new AccountBlockedException(user.getBlockedUntil());
            }
            // Le blocage a expiré : on nettoie le champ
            user.setBlockedUntil(null);
            userRepository.save(user);
        }

        String accessToken = jwtUtils.generateAccessToken(user.getEmail());
        String refreshToken = jwtUtils.generateRefreshToken(user.getEmail());

        UserResponse userResponse = UserResponse.builder()
            .id(user.getId()).firstName(user.getFirstName()).lastName(user.getLastName())
            .email(user.getEmail()).role(user.getRole()).specialization(user.getSpecialization())
            .avatarUrl(user.getAvatarUrl()).bio(user.getBio()).enabled(user.isEnabled()).createdAt(user.getCreatedAt()).build();

        return AuthResponse.builder()
            .accessToken(accessToken).refreshToken(refreshToken)
            .tokenType("Bearer").user(userResponse).build();
    }

    @Override
    @Transactional
    public ApiResponse<String> verifyEmail(String token) {
        User user = userRepository.findByVerificationToken(token)
            .orElseThrow(() -> new RuntimeException("Token invalide"));

        if (user.getVerificationTokenExpiry().isBefore(LocalDateTime.now())) {
            return ApiResponse.error("Token expiré");
        }

        user.setVerificationToken(null);
        user.setVerificationTokenExpiry(null);
        user.setRegistrationStatus("PAYMENT_PENDING");
        userRepository.save(user);

        return ApiResponse.success("Email vérifié. Veuillez procéder au paiement.", null);
    }

    @Override
    @Transactional
    public ApiResponse<String> forgotPassword(String email) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user != null) {
            String token = UUID.randomUUID().toString();
            user.setResetPasswordToken(token);
            user.setResetPasswordTokenExpiry(LocalDateTime.now().plusHours(2));
            userRepository.save(user);
            emailService.sendPasswordResetEmail(email, user.getFirstName(), token);
        }
        return ApiResponse.success("Si cet email existe, un lien de réinitialisation a été envoyé.", null);
    }

    @Override
    @Transactional
    public ApiResponse<String> resetPassword(String token, String newPassword) {
        User user = userRepository.findByResetPasswordToken(token)
            .orElseThrow(() -> new RuntimeException("Token invalide"));

        if (user.getResetPasswordTokenExpiry().isBefore(LocalDateTime.now())) {
            return ApiResponse.error("Token expiré");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setResetPasswordToken(null);
        user.setResetPasswordTokenExpiry(null);
        userRepository.save(user);

        return ApiResponse.success("Mot de passe réinitialisé avec succès.", null);
    }
}
