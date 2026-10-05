package com.elearning.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Pièce du dossier d'inscription scannée en PDF : attestation et relevé du bac (nouveau bachelier),
 * relevés de l'année passée et attestation de réussite (étudiant venant d'un autre établissement).
 * Le fichier est rangé hors du dossier public /uploads et n'est servi qu'à l'administration.
 */
@Entity @Table(name = "school_documents")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SchoolDocument {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "enrollment_id")
    private SchoolEnrollment enrollment;
    @Column(nullable = false, length = 30) private String type;   // BAC_ATTESTATION | BAC_TRANSCRIPT | PREVIOUS_TRANSCRIPT | SUCCESS_ATTESTATION
    @Column(nullable = false) private String originalName;
    @Column(nullable = false) private String storedName;
    @Column(nullable = false) private long size;
    @Column(nullable = false) private LocalDateTime uploadedAt;
}
