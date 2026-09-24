package com.elearning.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.ArrayList;
import java.util.List;

@Entity @Table(name = "qcm_questions")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class QcmQuestion {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "qcm_id", nullable = false)
    @ToString.Exclude
    private Qcm qcm;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @Builder.Default
    private Integer points = 1;

    @Column(nullable = false)
    private Integer orderIndex;

    // QCM, PRACTICAL, CASE ou LONG_TEXT. Les résultats de cas / texte long peuvent être évalués manuellement.
    @Column
    @Builder.Default
    private String questionType = "QCM";

    @Column(columnDefinition = "TEXT")
    private String correctionData;

    @Column(columnDefinition = "TEXT")
    private String caseScenario;

    @Column(columnDefinition = "TEXT")
    private String expectedAnswer;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    @Builder.Default
    private List<QcmChoice> choices = new ArrayList<>();
}
