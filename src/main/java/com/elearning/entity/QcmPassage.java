package com.elearning.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity @Table(name = "qcm_passages")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class QcmPassage {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "qcm_id", nullable = false)
    @ToString.Exclude
    private Qcm qcm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    @ToString.Exclude
    private User student;

    @CreationTimestamp
    private LocalDateTime startedAt;

    private LocalDateTime submittedAt;

    @Builder.Default
    private Boolean isSubmitted = false;

    private Integer score;    // points obtenus
    private Integer maxScore; // total possible

    @Column(columnDefinition = "TEXT")
    private String documentAnswer;

    private String paperCorrectionUrl;
    private String paperCorrectionFilename;

    private Integer manualScore;

    @Column(columnDefinition = "TEXT")
    private String manualCorrectionNote;

    private Integer ocrScore;

    @Column(columnDefinition = "TEXT")
    private String ocrCorrectionNote;

    // Exclusion pour violations répétées du mode plein écran (anti-triche)
    @Builder.Default
    private Boolean excludedForViolations = false;

    private LocalDateTime excludedAt;

    @OneToMany(mappedBy = "passage", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<QcmReponse> reponses = new ArrayList<>();

    @OneToMany(mappedBy = "passage", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<QcmFullscreenViolation> fullscreenViolations = new ArrayList<>();
}
