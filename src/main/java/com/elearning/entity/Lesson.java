package com.elearning.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name = "lessons")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Lesson {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private String title;
    @Column(columnDefinition = "TEXT") private String description;
    @Column(columnDefinition = "TEXT") private String content;
    private String videoUrl;
    private String pdfUrl;
    private Integer duration;
    private Integer orderIndex;
    @Enumerated(EnumType.STRING) private LessonType type;

    @Column(columnDefinition = "TEXT") private String starterCode;
    private String language;

    /** Apparaît dans l'onglet "Pratique" du lecteur de cours, même pour un type PDF/VIDEO/QUIZ. */
    @Column(nullable = false) private boolean exercise;

   @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private Course course;

    /** Renseigné uniquement pour les exercices autonomes (non rattachés à un cours) : leur créateur. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id")
    private User teacher;

    public enum LessonType { VIDEO, PDF, QUIZ, CODE_EXERCISE, EXCEL_EXERCISE }
}
