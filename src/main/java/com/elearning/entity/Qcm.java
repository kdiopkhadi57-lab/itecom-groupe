package com.elearning.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity @Table(name = "qcms")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Qcm {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String subjectFileUrl;
    private String correctionFileUrl;

    @Column(columnDefinition = "TEXT")
    private String subjectText;

    @Column(columnDefinition = "TEXT")
    private String correctionText;

    // Durée estimée du QCM en minutes, utilisée pour calculer le déblocage
    // d'un étudiant exclu pour violations anti-triche.
    private Integer estimatedDurationMinutes;

    @Builder.Default
    private Boolean paperCorrectionRequired = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "professor_id", nullable = false)
    @ToString.Exclude
    private User professor;

    @OneToMany(mappedBy = "qcm", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    @Builder.Default
    private List<QcmQuestion> questions = new ArrayList<>();

    // Liste d'étudiants assignés. Si vide → visible par tous les étudiants approuvés.
    @OneToMany(mappedBy = "qcm", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<QcmStudent> assignedStudents = new ArrayList<>();

    // DRAFT | PUBLISHED
    @Column(nullable = false)
    @Builder.Default
    private String status = "DRAFT";

    @CreationTimestamp
    private LocalDateTime createdAt;
}
