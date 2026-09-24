package com.elearning.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;
import java.util.*;

@Entity @Table(name = "users")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private String firstName;
    @Column(nullable = false) private String lastName;
    @Column(nullable = false, unique = true) private String email;
    @Column(nullable = false) private String password;
    @Enumerated(EnumType.STRING) private Role role;
    private String specialization;
    private String avatarUrl;
    private String bio;
    private String phone;
    @Column(nullable = false) private boolean enabled = false;
    private String verificationToken;
    private LocalDateTime verificationTokenExpiry;
    private String resetPasswordToken;
    private LocalDateTime resetPasswordTokenExpiry;

    // Blocage temporaire de connexion (ex: exclusion pour violations anti-triche lors d'un examen/QCM)
    private LocalDateTime blockedUntil;

    // Inscription avec paiement : EMAIL_PENDING → PAYMENT_PENDING → PAYMENT_SUBMITTED → APPROVED / REJECTED
    @Column(nullable = false, columnDefinition = "VARCHAR(50) DEFAULT 'APPROVED'") @Builder.Default
    private String registrationStatus = "EMAIL_PENDING";
    private String paymentMethod;   // WAVE | ORANGE_MONEY
    private String paymentPhone;
    private String paymentReference;
    private LocalDateTime paymentSubmittedAt;
    @CreationTimestamp private LocalDateTime createdAt;
    @UpdateTimestamp private LocalDateTime updatedAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "user_enrollments",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "course_id"))
    @Builder.Default
    private List<Course> enrolledCourses = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Progress> progressList = new ArrayList<>();
}
