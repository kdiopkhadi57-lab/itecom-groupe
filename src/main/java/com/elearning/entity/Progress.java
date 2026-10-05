package com.elearning.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

@Entity @Table(name = "progress", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "lesson_id"})
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Progress {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id") private User user;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "lesson_id") private Lesson lesson;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "course_id") private Course course;
    private boolean completed = false;
    private Integer watchedSeconds = 0;
    private Double percentage = 0.0;

    /** Vidéo : position de reprise, durée et plages réellement regardées ([[début, fin], …] en secondes, JSON). */
    private Double videoPosition;
    private Double videoDuration;
    @Column(columnDefinition = "TEXT") private String watchedRanges;

    /** Travail en cours de l'élève sur cette leçon/exercice (code et/ou projet Java multi-fichiers), en JSON. */
    @Column(columnDefinition = "TEXT") private String savedCode;

    @UpdateTimestamp private LocalDateTime lastUpdated;
}
