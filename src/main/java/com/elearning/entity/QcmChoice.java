package com.elearning.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name = "qcm_choices")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class QcmChoice {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    @ToString.Exclude
    private QcmQuestion question;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String choiceText;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isCorrect = false;

    @Column(nullable = false)
    private Integer orderIndex;
}
