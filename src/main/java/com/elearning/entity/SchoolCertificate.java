package com.elearning.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/** Attestation délivrée par l'administration, vérifiable publiquement par son code (QR code). */
@Entity @Table(name = "school_certificates")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SchoolCertificate {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.EAGER, optional = false) @JoinColumn(name = "enrollment_id")
    private SchoolEnrollment enrollment;
    @Column(nullable = false, length = 20) private String type;   // INSCRIPTION | SCOLARITE | REUSSITE | RELEVE_NOTES
    @Column(nullable = false, unique = true, length = 20) private String verificationCode;
    @Column(nullable = false, unique = true, length = 30) private String reference;
    // Mention et moyenne figées au moment de la délivrance (attestation de réussite, relevé)
    private Double average;
    private String mention;
    @Column(nullable = false) private LocalDateTime issuedAt;
    private String issuedBy;
    @Column(nullable = false) @Builder.Default private boolean revoked = false;
    private LocalDateTime revokedAt;
}
