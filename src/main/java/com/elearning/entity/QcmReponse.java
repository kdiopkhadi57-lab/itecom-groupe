package com.elearning.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name = "qcm_reponses")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class QcmReponse {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "passage_id", nullable = false)
    @ToString.Exclude
    private QcmPassage passage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private QcmQuestion question;

    // Le choix sélectionné par l'étudiant (null = pas répondu)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "choice_id")
    private QcmChoice choiceSelected;

    // Réponse rédigée par l'étudiant (cas pratique, réponse longue)
    @Column(columnDefinition = "TEXT")
    private String textAnswer;

    // Calculé lors de la soumission
    @Builder.Default
    private Boolean isCorrect = false;
}
