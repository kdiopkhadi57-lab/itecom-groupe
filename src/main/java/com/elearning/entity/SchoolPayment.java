package com.elearning.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Paiement de scolarité. Un paiement mobile déclaré par l'étudiant est PENDING jusqu'à ce que
 * l'administration le rapproche de son relevé Wave / Orange Money / Free Money ; un paiement saisi
 * par l'administration (espèces, virement…) est validé directement.
 */
@Entity @Table(name = "school_payments")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SchoolPayment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.EAGER, optional = false) @JoinColumn(name = "enrollment_id")
    private SchoolEnrollment enrollment;
    @Column(nullable = false) private long amount;
    @Column(nullable = false, length = 20) private String purpose;   // INSCRIPTION | SCOLARITE
    @Column(nullable = false, length = 20) private String method;    // WAVE | ORANGE_MONEY | FREE_MONEY | ESPECES | VIREMENT
    private String phone;
    private String transactionRef;
    @Column(nullable = false, length = 20) @Builder.Default private String status = "PENDING";  // PENDING | VALIDATED | REJECTED
    @Column(unique = true, length = 30) private String receiptNumber;
    @Column(unique = true, length = 20) private String verificationCode;
    @Column(length = 500) private String rejectionReason;
    private String processedBy;
    @Column(nullable = false) private LocalDateTime submittedAt;
    private LocalDateTime processedAt;
}
