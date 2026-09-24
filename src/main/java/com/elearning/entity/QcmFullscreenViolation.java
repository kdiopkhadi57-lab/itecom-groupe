package com.elearning.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "qcm_fullscreen_violations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QcmFullscreenViolation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "qcm_passage_id", nullable = false)
    @ToString.Exclude
    private QcmPassage passage;

    @Column(nullable = false)
    private Integer violationNumber; // 1, 2

    @Column(nullable = false)
    private LocalDateTime violationTime;

    @Column(columnDefinition = "TEXT")
    private String details; // e.g., "Sortie fullscreen - Tentative 1/2"

    @PrePersist
    protected void onCreate() {
        violationTime = LocalDateTime.now();
    }
}
