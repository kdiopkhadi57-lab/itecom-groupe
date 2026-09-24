package com.elearning.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "fullscreen_violations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FullscreenViolation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_submission_id", nullable = false)
    @ToString.Exclude
    private ExamSubmission submission;

    @Column(nullable = false)
    private Integer violationNumber; // 1, 2, 3

    @Column(nullable = false)
    private LocalDateTime violationTime;

    private LocalDateTime recoveredAt;

    @Column(columnDefinition = "TEXT")
    private String details; // e.g., "30 secondes avant interruption", "Interruption de session"

    @PrePersist
    protected void onCreate() {
        violationTime = LocalDateTime.now();
    }
}
