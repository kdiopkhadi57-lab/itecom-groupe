package com.elearning.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "exam_submissions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_student_id", nullable = false)
    @ToString.Exclude
    private ExamStudent examStudent;

    @Column(nullable = false)
    private LocalDateTime submittedAt;

    private LocalDateTime gradedAt;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isGraded = false;

    private Double totalScore;

    private Double maxScore;

    @Column(columnDefinition = "TEXT")
    private String aiReport;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private SubmissionType submissionType = SubmissionType.ONLINE;

    @ElementCollection
    @CollectionTable(name = "exam_submission_scans", joinColumns = @JoinColumn(name = "submission_id"))
    @Column(name = "file_url", columnDefinition = "TEXT")
    @OrderColumn(name = "page_index")
    @Builder.Default
    private List<String> scannedFileUrls = new ArrayList<>();

    @OneToMany(mappedBy = "submission", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ExamAnswer> answers = new ArrayList<>();

    @OneToMany(mappedBy = "submission", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<FullscreenViolation> fullscreenViolations = new ArrayList<>();
}
