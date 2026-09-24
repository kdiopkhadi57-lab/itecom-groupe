package com.elearning.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "exam_questions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_id", nullable = false)
    @ToString.Exclude
    private Exam exam;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String referenceAnswer;

    @Column(nullable = false)
    private Integer maxScore;

    @Column(nullable = false)
    private Integer orderIndex;

    @Column
    @Builder.Default
    private String questionType = "QCM";

    @Column(columnDefinition = "TEXT")
    private String correctionData;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    @Builder.Default
    private List<ExamChoice> choices = new ArrayList<>();
}
