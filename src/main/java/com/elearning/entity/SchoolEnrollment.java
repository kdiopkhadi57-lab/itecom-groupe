package com.elearning.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

/**
 * Inscription administrative d'un étudiant pour une année universitaire.
 * PENDING (frais d'inscription non réglés) → ACTIVE ; SUSPENDED / CANCELLED décidés par l'administration.
 */
@Entity
@Table(name = "school_enrollments",
       uniqueConstraints = @UniqueConstraint(columnNames = {"student_id", "academic_year"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SchoolEnrollment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.EAGER, optional = false) @JoinColumn(name = "student_id")
    private User student;
    @Column(name = "academic_year", nullable = false, length = 9) private String academicYear;
    @Column(nullable = false, length = 5) private String level;
    private String specialization;
    @Column(nullable = false, unique = true, length = 30) private String matricule;
    @Column(nullable = false) private long registrationFee;
    @Column(nullable = false) private long tuitionFee;
    @Column(nullable = false) @Builder.Default private long discount = 0;
    @Column(nullable = false) @Builder.Default private int installments = 1;
    @Column(nullable = false, length = 20) @Builder.Default private String status = "PENDING";
    @CreationTimestamp private LocalDateTime createdAt;
    @UpdateTimestamp private LocalDateTime updatedAt;

    public long totalDue() {
        return Math.max(0, registrationFee + tuitionFee - discount);
    }
}
