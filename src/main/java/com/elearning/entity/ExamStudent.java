package com.elearning.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "exam_students")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamStudent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_id", nullable = false)
    @ToString.Exclude
    private Exam exam;

    @Column(nullable = false)
    private String studentName;

    @Column(nullable = false)
    private String studentEmail;

    @Column(nullable = false, unique = true)
    private String accessToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private StudentExamStatus status = StudentExamStatus.INVITED;

    private LocalDateTime invitedAt;

    private LocalDateTime startedAt;

    // Exclusion pour violations répétées du mode plein écran (anti-triche)
    @Builder.Default
    private Boolean excludedForViolations = false;

    private LocalDateTime excludedAt;

    @OneToOne(mappedBy = "examStudent", cascade = CascadeType.ALL, orphanRemoval = true)
    private ExamSubmission submission;
}
